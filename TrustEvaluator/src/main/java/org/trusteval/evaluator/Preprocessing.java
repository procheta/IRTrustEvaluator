/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.evaluator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.trusteval.indexing.TrecDocIndexer;

/**
 *
 * @author Procheta
 */
public class Preprocessing {

    TrecDocIndexer indexer;
    Properties prop;
    IndexReader reader;

    public Preprocessing(String propFile) throws Exception {

        indexer = new TrecDocIndexer(propFile);
        prop = indexer.getProperties();
        File indexDir = indexer.getIndexDir();
        System.out.println("Running queries against index: " + indexDir.getPath());

        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));

    }

    public void prepareDocFile() throws IOException {

        FileWriter fw = new FileWriter(new File(prop.getProperty("docFile")));
        BufferedWriter bw = new BufferedWriter(fw);
        for (int i = 0; i < reader.numDocs(); i++) {
            Document doc = reader.document(i);
            String id = doc.get("id");
            String words = doc.get("words");
            bw.write(id + "\t"+ words);
            bw.newLine();       
        }
        
        bw.close();
    }
    
    public static void main(String[] args) throws Exception{
    
    Preprocessing pp = new Preprocessing("retrieve.properties");
    pp.prepareDocFile();
    
    }

}
