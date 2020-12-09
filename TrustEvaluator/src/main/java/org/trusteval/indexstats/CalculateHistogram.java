/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.indexstats;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.trusteval.indexing.TrecDocIndexer;
import static org.trusteval.indexing.TrecDocIndexer.FIELD_ID;
import static org.trusteval.indexstats.Utility.readQrelFile;
import org.trusteval.trec.MsMarcoQueryParser;
import org.trusteval.trec.QueryObject;
import org.trusteval.wvec.WordVec;
import org.trusteval.wvec.WordVecs;

/**
 *
 * @author dwaipayan
 */
public class CalculateHistogram {

    WordVecs wvs;
    Properties prop;
    TrecDocIndexer indexer;
    IndexSearcher searcher;
    List<QueryObject> queries;
    GetStats stats;
    String fieldName;
    HashMap<String, KnownRelevance> allKnownJudgement;  // to contain the judged documents for each query

    public CalculateHistogram(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        indexer = new TrecDocIndexer(propFile);

        File indexDir = indexer.getIndexDir();
 
        searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(indexDir.toPath())));

        queries = constructQueries();
//        for (QueryObject qo : queries) {
//            System.out.println(qo.id + " " + qo.title);
//        }
        stats = new GetStats(propFile);
        fieldName = prop.getProperty("fieldName", "words");

        allKnownJudgement = readQrelFile(prop.getProperty("qrels.file"));
        System.out.println("Vector loading started...");
        wvs = new WordVecs(prop);
        System.out.println("Done.");
    }

    public List<QueryObject> constructQueries() throws Exception {

        List<QueryObject> queries = new ArrayList<>();
        String queryFile = prop.getProperty("query.file");
//        TRECQueryParser parser = new TRECQueryParser(queryFile, indexer.getAnalyzer(), false, prop.getProperty("fieldName"));
        MsMarcoQueryParser parser = new MsMarcoQueryParser(queryFile, indexer.getAnalyzer());
        queries = parser.makeQuery();
        return queries;
    }

    /**
     * Returns a set with all the query terms
     * @return 
     */
    public Set getAllQueryTerms() {
        System.out.println("Getting all query terms...");
        Set<String> queryTerms = new HashSet<>();
        for (QueryObject query : queries) {
            List qterms = query.getQueryTerms(fieldName);
            queryTerms.addAll(qterms);
        }

        System.out.println("Done.");
        return queryTerms;
    }

    /**
     * Make the bin for one query-document pair
     * @param binSize
     * @param qterms
     * @param docTerms 
     * @return  
     */
    public float[] makeBin(int binSize, List<String> qterms, String[] docTerms) {

        float [] oneDoc = new float[0];
        WordVec qtv, dtv; // query and document word vector
        float cossim;

        // for each query term
        for (String qterm : qterms) {
            float oneQterm[] = new float[binSize];
            qtv = wvs.getVec(qterm);
            if(qtv != null) {
                // for each document term
                for (String docTerm : docTerms) {
                    dtv = wvs.getVec(docTerm);
                    if(dtv != null) {
                        // compute the cosine similarity between the query term and document term
                        cossim = qtv.cosineSim(dtv);
                        int vid = (int) ((cossim+1.0) /2 * (binSize-1));  
                        oneQterm[vid] += 1;
                    }
                }
                oneDoc = ArrayUtils.addAll(oneDoc, oneQterm);
            }
        }
        return oneDoc;
    }

    public void makeHistogram() throws IOException, Exception {
        
	PrintWriter writer = new PrintWriter(prop.getProperty("histogramFile", "histogram.txt"));

        Set<String> allQueryTerms = getAllQueryTerms();
        HashMap<String, Double> termIdf;

        // get idfs of all query terms
        termIdf = stats.getAllIDF(fieldName, allQueryTerms);

//        System.exit(1);
        // for each query in the topic set
        for (QueryObject query : queries) {

            System.out.println(query.id + " " + query.title);
            List<String> qterms = query.getQueryTerms(fieldName);

            KnownRelevance kr = allKnownJudgement.get(query.id);
            if (kr != null) {
                List<String> judgedRel = kr.relevant;
                List<String> judgedNonrel = kr.nonrelevant;
                List<String> allJudged = new ArrayList<>();
                allJudged.addAll(judgedRel);
                allJudged.addAll(judgedNonrel);

                // for each judged rel docs for that query:
                for (String doc : judgedRel) {
                    int luceneDocid = Utility.getLuceneDocid(doc, searcher, FIELD_ID);
                    String docTerms = searcher.doc(luceneDocid).get("words");   // hard coding - VERY BAD
                    docTerms = Utility.analyzeText(indexer.getAnalyzer(), docTerms, "words");
                    writer.print(query.id + " " + doc + " " + "1" + " " + qterms.size() + " ");
                    for (String qterm : qterms) {
                        Double idf = termIdf.get(qterm);
                        if (idf == null) 
                            idf = 0.0;
                        writer.write(idf.toString() + " ");
                    }
                    float [] onedoc = makeBin(20, qterms, docTerms.split(" "));
                    StringBuilder builder = new StringBuilder();
                    for (float f: onedoc) {
                        builder.append((f==0?0.0:Math.log10(f)) + " ");
                    }
                    writer.write(builder.toString());
                    writer.write("\n");
                }

                // for each judged non rel docs.
                for (String doc : judgedNonrel) {
                    int luceneDocid = Utility.getLuceneDocid(doc, searcher, FIELD_ID);
                    String docTerms = searcher.doc(luceneDocid).get("words");   // hard coding - VERY BAD
                    docTerms = Utility.analyzeText(indexer.getAnalyzer(), docTerms, "words");
                    writer.print(query.id + " " + doc + " " + "0" + " " + qterms.size() + " ");
                    for (String qterm : qterms) {
                        Double idf = termIdf.get(qterm);
                        if (idf == null) 
                            idf = 0.0;
                        writer.write(idf.toString() + " ");
                    }

                    float [] onedoc = makeBin(20, qterms, docTerms.split(" "));
                    StringBuilder builder = new StringBuilder();
                    for (float f: onedoc) {
                        builder.append((f==0?0.0:Math.log10(f)) + " ");
                    }
                    writer.write(builder.toString());
                    writer.write("\n");
                }
            }
        }
        writer.close();
    }

    public static void main(String[] args) {

        if (args.length < 1) {
            args = new String[1];
            args[0] = "foo.properties";
        }

        try {
            CalculateHistogram calHist = new CalculateHistogram(args[0]);

            calHist.makeHistogram();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
