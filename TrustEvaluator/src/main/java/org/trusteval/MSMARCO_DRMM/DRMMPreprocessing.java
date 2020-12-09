/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.MSMARCO_DRMM;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.trusteval.trec.TRECQueryParser;

/**
 *
 * @author Procheta
 */
public class DRMMPreprocessing {

    String tripleFile;
    IndexReader reader;
    IndexSearcher searcher;
    String corpusFile;
    String queryFile;
    HashMap<String, String> qidMap;
    String topicFile;

    public DRMMPreprocessing(Properties prop) throws IOException {
        tripleFile = prop.getProperty("DRMMtriple");
        queryFile = prop.getProperty("DRMMquery");
        File indexDir = new File(prop.getProperty("index"));
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);
        corpusFile = prop.getProperty("corpusFile");
        topicFile = prop.getProperty("topicFile");
        loadQueries();
    }

    public void loadQueries() throws FileNotFoundException, IOException {
        FileReader fr = new FileReader(new File(topicFile));
        BufferedReader br = new BufferedReader(fr);

        String line = br.readLine();

        while (line != null) {
            String st[] = line.split("\t");
            qidMap.put(st[1], st[0]);
            line = br.readLine();
        }
    }

    public String findQueryId(String query) {

        return qidMap.get(query);
    }

    public String findPassageId(String passage) throws IOException, Exception {
        TRECQueryParser tp = new TRECQueryParser();
        passage = tp.analyze(passage, "stop.txt");
        BooleanQuery bq = new BooleanQuery();
        String st[] = passage.split("\\s+");
        for (String word : st) {
            TermQuery tq = new TermQuery(new Term("words", word));
            bq.add(tq, BooleanClause.Occur.MUST);
        }
        TopDocs tdocs = searcher.search(bq, 1);
        String docID = reader.document(tdocs.scoreDocs[0].doc).get("id");

        return docID;
    }

    public void prepareTripleFile(String fileName, BufferedWriter bw1, BufferedWriter bw2) throws FileNotFoundException, IOException, Exception {
        FileReader fr = new FileReader(new File(tripleFile));
        BufferedReader br = new BufferedReader(fr);
        String line = br.readLine();

        HashSet<String> qids = new HashSet<String>();
        FileWriter fw = new FileWriter(new File(fileName));
        BufferedWriter bw = new BufferedWriter(fw);
        while (line != null) {
            String st[] = line.split("\t");
            String qid = findQueryId(st[0]);
            String positiveDocId = findPassageId(st[1]);
            String negativeDocId = findPassageId(st[2]);
            bw.write(qid + "\t" + positiveDocId + "\t" + negativeDocId);
            bw.newLine();

            if (!qids.contains(st[0])) {
                bw1.write(qid + "\t" + st[0]);
                bw1.newLine();
                qids.add(st[0]);
            }
            if (!qids.contains(st[1])) {
                bw2.write(positiveDocId + "\t" + st[1]);
                bw2.newLine();
                qids.add(st[1]);
            }
            if (!qids.contains(st[2])) {
                bw2.write(negativeDocId + "\t" + st[2]);
                bw2.newLine();
                 qids.add(st[1]);
            }
            line = br.readLine();
        }
        bw.close();
    }

    public void preprocessDRMMFIles(Properties prop) throws Exception {

        FileWriter fw1 = new FileWriter(new File(prop.getProperty("topicFile")));
        BufferedWriter bw1 = new BufferedWriter(fw1);

        FileWriter fw2 = new FileWriter(new File(prop.getProperty("docFile")));
        BufferedWriter bw2 = new BufferedWriter(fw2);

        prepareTripleFile(prop.getProperty("outputTripple"), bw1, bw2);
        bw1.close();
        bw2.close();
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException, Exception{
        
        Properties prop = new Properties();
        prop.load(new FileReader(new File("retrieve.properties")));
        DRMMPreprocessing drp = new DRMMPreprocessing(prop);
        drp.preprocessDRMMFIles(prop);   
    }

}
