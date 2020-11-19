/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.retriever;

/**
 *
 * @author Debasis
 */
import org.trusteval.evaluator.Evaluator;
import org.trusteval.feedback.RelevanceModelConditional;
import org.trusteval.feedback.RelevanceModelIId;
import org.trusteval.feedback.RetrievedDocTermInfo;
import org.trusteval.indexing.TrecDocIndexer;
import java.io.*;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.*;
import org.trusteval.feedback.OneDimKDE;
import org.trusteval.feedback.TwoDimKDE;
import org.trusteval.trec.QueryObject;
import org.trusteval.trec.TRECQueryParser;
import org.trusteval.wvec.WordVec;
import org.trusteval.wvec.WordVecs;

/**
 *
 * @author Debasis
 */
public class TrecDocRetriever {

    TrecDocIndexer indexer;
    IndexReader reader;
    IndexSearcher searcher;
    int numWanted;
    Properties prop;
    String runName;
    String kdeType;
    boolean postRLMQE;
    boolean postQERerank;
    Similarity model;
    boolean preretievalExpansion;
    boolean debugMode;

    public TrecDocRetriever(String propFile) throws Exception {
        indexer = new TrecDocIndexer(propFile);
        prop = indexer.getProperties();
        File indexDir = indexer.getIndexDir();
        System.out.println("Running queries against index: " + indexDir.getPath());

        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);

        String retrieverModel = prop.getProperty("retrieveModel");
        if (retrieverModel.equals("BM25")) {
            float k = Float.parseFloat(prop.getProperty("k", "1"));
            float b = Float.parseFloat(prop.getProperty("b", "0.7f"));
            this.model = new BM25Similarity(k, b);
        } else {
            float lambda = Float.parseFloat(prop.getProperty("lambda", "0.5f"));
            this.model = new LMJelinekMercerSimilarity(lambda);
        }
        searcher.setSimilarity(model);

        numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "1000"));
        runName = prop.getProperty("retrieve.runname", "bm");

        kdeType = prop.getProperty("rlm.type", "uni");
        preretievalExpansion = Boolean.parseBoolean(prop.getProperty("preretievalExpansion", "false"));
        debugMode = Boolean.parseBoolean(prop.getProperty("debugMode"));

    }

    public Properties getProperties() {
        return prop;
    }

    public IndexReader getReader() {
        return reader;
    }

    public List<QueryObject> constructQueries() throws Exception {
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile, indexer.getAnalyzer(), preretievalExpansion, prop.getProperty("fieldName"));

        if (preretievalExpansion) {
            parser.addExpansionTerms();
        }

        String collection = prop.getProperty("collection");
        if (collection.equals("Trec")) {
            return parser.findQueries(queryFile, prop);
        } else if (collection.equals("MSMARCO")) {
            return parser.loadMSMarcoQueries(queryFile, prop);
        } else {
            parser.parse();
            return parser.getQueries();
        }
    }

    // Computes the similarity of two queries based on KL-divergence
    // of the estimated relevance models. More precisely, if Q1 and Q2
    // are two queries, the function computes pi = P(w|Qi,TOP(Qi)) for i=1,2
    // It then computes KL(p1, p2)
    public float computeQuerySimilarity(QueryObject q1, QueryObject q2, int ntop, WordVecs wvec) throws Exception {

        // Get the top docs for both q1 and q2
        TopDocs q1_topDocs = searcher.search(q1.getLuceneQueryObj(), ntop);
        TopDocs q2_topDocs = searcher.search(q2.getLuceneQueryObj(), ntop);

        // Estimate the relevance models for each query and associated top-docs
        RelevanceModelIId rm_q1 = new RelevanceModelConditional(this, q1, q1_topDocs, wvec);
        RelevanceModelIId rm_q2 = new RelevanceModelConditional(this, q2, q2_topDocs, wvec);

        Map<String, RetrievedDocTermInfo> q1_topTermMap = rm_q1.getRetrievedDocsTermStats().getTermStats();
        Map<String, RetrievedDocTermInfo> q2_topTermMap = rm_q2.getRetrievedDocsTermStats().getTermStats();

        // Merge the two models
        Map<String, RetrievedDocTermInfo> mergedAvgModel
                = mergeRelevanceModels(q1_topTermMap, q2_topTermMap);
        // JS divergence
        return (klDiv(q1_topTermMap, mergedAvgModel) + klDiv(q2_topTermMap, mergedAvgModel)) / 2;
    }

    Map<String, RetrievedDocTermInfo> mergeRelevanceModels(
            Map<String, RetrievedDocTermInfo> q1_topTermMap,
            Map<String, RetrievedDocTermInfo> q2_topTermMap) {

        float wt = 0;
        Map<String, RetrievedDocTermInfo> merged_topTermMap = new HashMap<>();

        for (RetrievedDocTermInfo a : q1_topTermMap.values()) {
            RetrievedDocTermInfo b = q2_topTermMap.get(a.getTerm());
            wt = a.getWeight();
            if (b != null) {
                wt += b.getWeight();
            }

            a.setWeight(wt / 2);
            merged_topTermMap.put(a.getTerm(), a);
        }

        for (RetrievedDocTermInfo a : q2_topTermMap.values()) {
            RetrievedDocTermInfo b = q1_topTermMap.get(a.getTerm());
            wt = a.getWeight();
            if (b != null) {
                wt += b.getWeight();
            }

            a.setWeight(wt / 2);
            merged_topTermMap.put(a.getTerm(), a);
        }

        return merged_topTermMap;
    }

    float klDiv(Map<String, RetrievedDocTermInfo> q1_topTermMap, Map<String, RetrievedDocTermInfo> q2_topTermMap) {
        float kldiv = 0, a_wt, b_wt;

        for (RetrievedDocTermInfo a : q1_topTermMap.values()) {
            String term = a.getTerm(); // for each term in model the first model

            // Get this term's weight in the second model
            RetrievedDocTermInfo b = q2_topTermMap.get(term);
            if (b == null) {
                continue;
            }

            a_wt = a.getWeight();
            b_wt = b.getWeight();
            kldiv += a_wt * Math.log(a_wt / b_wt);
        }
        return kldiv;
    }

    TopDocs retrieve(QueryObject query) throws IOException {

        return searcher.search(query.getLuceneQueryObj(), numWanted);
    }

    public void retrieveAll() throws Exception {
        TopDocs topDocs;
        String resultsFile = prop.getProperty("res.file");
        FileWriter fw = new FileWriter(resultsFile);

        List<QueryObject> queries = constructQueries();

        boolean toExpand = Boolean.parseBoolean(prop.getProperty("preretrieval.queryexpansion", "false"));
        // Expand all queries
        if (toExpand) {
            NNQueryExpander nnQexpander = new NNQueryExpander(prop);
            nnQexpander.expandQueriesWithNN(queries);
        }
        WordVecs wvec = null;
        if (prop.getProperty("rlm.type").equals("bi")) {
            wvec = new WordVecs(prop);
        }
        int count = 0;

        for (QueryObject query : queries) {

            // Print query
            if (debugMode) {
                if (count == 6) {
                    //continue;
                }
                System.out.println("Executing query: " + query.id + " " + query);

            }
            // Retrieve results
            topDocs = retrieve(query);

            // Apply feedback
            if (Boolean.parseBoolean(prop.getProperty("feedback")) && topDocs.scoreDocs.length > 0) {

                topDocs = applyFeedback(query, topDocs, wvec);
            }
            // Save results
            //System.out.println("Num topdocs "+ topDocs.totalHits);
            saveRetrievedTuples(fw, query, topDocs);

            if (debugMode) {
                count++;
            }
        }

        fw.close();
        reader.close();

        if (Boolean.parseBoolean(prop.getProperty("eval"))) {
            evaluate(prop.getProperty("evalMode"));
        }
    }

    public TopDocs applyFeedback(QueryObject query, TopDocs topDocs, WordVecs wvec) throws Exception {
        RelevanceModelIId fdbkModel;

        fdbkModel = kdeType.equals("uni") ? new OneDimKDE(this, query, topDocs, wvec)
                : kdeType.equals("bi") ? new TwoDimKDE(this, query, topDocs, wvec)
                : kdeType.equals("rlm_iid") ? new RelevanceModelIId(this, query, topDocs, wvec)
                : new RelevanceModelConditional(this, query, topDocs, wvec);

        try {
            if (kdeType.equals("rlm_conditional") || kdeType.equals("rlm_iid")) {
                fdbkModel.computeFdbkWeights(prop.getProperty("rlm.type"), null);
            } else {
                fdbkModel.computeKDE(prop.getProperty("rlm.type"), wvec.wordvecmap);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return topDocs;
        }
        if (Boolean.parseBoolean(prop.getProperty("clarity_compute", "false"))) {
            if (prop.getProperty("clarity.collmodel", "global").equals("global")) {
                System.out.println("Clarity: " + fdbkModel.getQueryClarity(reader));
            } else {
                System.out.println("Clarity: " + fdbkModel.getQueryClarity());
            }
        }

        // Post retrieval query expansion
        String rlmMode = prop.getProperty("rlm.type");
        QueryObject expandedQuery = null;
        if (rlmMode.equals("bi")) {
            expandedQuery = fdbkModel.expandQuery(prop.getProperty("rlm.type"), wvec.wordvecmap);
        } else {
            expandedQuery = fdbkModel.expandQuery(prop.getProperty("rlm.type"), null);
        }

        if(debugMode){
            System.out.println("Expanded Query "+ expandedQuery);
        }
        topDocs = searcher.search(expandedQuery.getLuceneQueryObj(), numWanted);
        return topDocs;
    }

    public void evaluate(String evalMode) throws Exception {
        Evaluator evaluator = new Evaluator(this.getProperties());
        evaluator.load(evalMode, debugMode);
        String collection = prop.getProperty("collection");
        if (evalMode.equals("trust")) {
            if (collection.equals("Trec")) {
                evaluator.loadQueryPairsTREC();
            } else {
                evaluator.loadQueryPairsMSMARCO();
            }
            System.out.println(evaluator.computeTrust());
        } else {
            evaluator.fillRelInfo(evalMode);
            System.out.println(evaluator.computeAll());
        }
    }

    public void saveRetrievedTuples(FileWriter fw, QueryObject query, TopDocs topDocs) throws Exception {
        StringBuffer buff = new StringBuffer();
        ScoreDoc[] hits = topDocs.scoreDocs;
        //System.out.println("One query result updated");
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            buff.append(query.id.trim()).append("\tQ0\t").
                    append(d.get(TrecDocIndexer.FIELD_ID)).append("\t").
                    append((i + 1)).append("\t").
                    append(hits[i].score).append("\t").
                    append(runName).append("\t").append(reader.document(docId).get(prop.getProperty("fieldName"))).append("\n");
        }
        fw.write(buff.toString());
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "retrieve.properties";
        }

        try {
            TrecDocRetriever searcher = new TrecDocRetriever(args[0]);
            searcher.retrieveAll();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
