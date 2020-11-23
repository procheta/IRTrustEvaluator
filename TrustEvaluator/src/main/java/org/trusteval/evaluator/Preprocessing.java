/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.evaluator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.trusteval.indexing.TrecDocIndexer;
import org.trusteval.trec.QueryObject;
import org.trusteval.trec.TRECQueryParser;

/**
 *
 * @author Procheta
 */
public class Preprocessing {

    TrecDocIndexer indexer;
    Properties prop;
    IndexReader reader;
    IndexSearcher searcher;

    public Preprocessing(String propFile) throws Exception {

        indexer = new TrecDocIndexer(propFile);
        prop = indexer.getProperties();
        File indexDir = indexer.getIndexDir();
        System.out.println("Running queries against index: " + indexDir.getPath());

        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());

    }

    public void prepareDocFile() throws IOException {

        FileWriter fw = new FileWriter(new File(prop.getProperty("docFile")));
        BufferedWriter bw = new BufferedWriter(fw);
        for (int i = 0; i < reader.numDocs(); i++) {
            Document doc = reader.document(i);
            String id = doc.get("id");
            String words = doc.get("words");
            bw.write(id + "\t" + words);
            bw.newLine();
        }

        bw.close();
    }

    public void prpareTopicFile() throws FileNotFoundException, Exception {
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile, indexer.getAnalyzer(), false, prop.getProperty("fieldName"));
        parser.parse();
        List<QueryObject> queries = parser.getQueries();

        FileWriter fw = new FileWriter(new File(prop.getProperty("topicFile")));
        BufferedWriter bw = new BufferedWriter(fw);
        System.out.println(queries.size());
        for (int i = 0; i < queries.size(); i++) {
            QueryObject q = queries.get(i);
            bw.write(String.valueOf(q.id) + "\t" + q.title);
            bw.newLine();
        }
        bw.close();

    }

    public void preparePreRankedFile() throws FileNotFoundException, Exception {

        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile, indexer.getAnalyzer(), false, prop.getProperty("fieldName"));
        parser.parse();
        List<QueryObject> queries = parser.getQueries();

        FileWriter fw = new FileWriter(new File(prop.getProperty("preRankedFile")));
        BufferedWriter bw = new BufferedWriter(fw);
        int count = 0;
        for (int i = 0; i < queries.size(); i++) {
            QueryObject q = queries.get(i);

            TopDocs tdocs = searcher.search(q.getLuceneQueryObj(), 2000);
            for(int i1 = 0; i1< tdocs.scoreDocs.length; i1++){
                Document doc = reader.document(tdocs.scoreDocs[i].doc);
                bw.write(q.id+ "\t"+ doc.get("title")+ "\t"+ doc.get("words"));
                bw.newLine();
            }
            bw.close();
        }

    }

    public static void main(String[] args) throws Exception {

        Preprocessing pp = new Preprocessing("retrieve.properties");
        //pp.prepareDocFile();
       // pp.prpareTopicFile();
       pp.preparePreRankedFile();

    }

}
