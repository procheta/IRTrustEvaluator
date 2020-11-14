/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.feedback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.trusteval.indexing.TrecDocIndexer;
import org.trusteval.trec.TRECQueryParser;
import org.trusteval.wvec.WordVec;

/**
 *
 * @author Debasis
 */
public class RetrievedDocsTermStats {
    
    TopDocs topDocs;
    IndexReader reader;
    int sumTf;
    float sumDf;
    float sumSim;
    Map<String, RetrievedDocTermInfo> termStats;
    List<PerDocTermVector> docTermVecs;
    int numTopDocs;
    
    public RetrievedDocsTermStats(IndexReader reader,
            TopDocs topDocs, int numTopDocs) {
        this.topDocs = topDocs;
        this.reader = reader;
        sumTf = 0;
        sumDf = numTopDocs;
        termStats = new HashMap<>();
        docTermVecs = new ArrayList<>();
        this.numTopDocs = numTopDocs;
    }
    
    public IndexReader getReader() {
        return reader;
    }
    
    public Map<String, RetrievedDocTermInfo> getTermStats() {
        return termStats;
    }
    
    public void buildAllStats(String rlmMode, HashMap<String, WordVec> wordVecMap) throws Exception {
        int rank = 0;
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            int docId = scoreDoc.doc;
            docTermVecs.add(buildStatsForSingleDoc(docId, rank, scoreDoc.score, rlmMode, wordVecMap));
            rank++;
        }
    }
    
    RetrievedDocTermInfo getTermStats(String qTerm) {
        return this.termStats.get(qTerm);
    }
    
    RetrievedDocTermInfo getTermStats(WordVec wv) {
        RetrievedDocTermInfo tInfo;
        String qTerm = wv.getWord();
        if (qTerm == null) {
            return null;
        }

        // Check if this word is a composed vector
        if (!wv.isComposed()) {
            tInfo = this.termStats.get(qTerm);
            return tInfo;
        }

        // Split up the composed into it's constituents
        String[] qTerms = qTerm.split(WordVec.COMPOSING_DELIM);
        RetrievedDocTermInfo firstTerm = this.termStats.get(qTerms[0]);
        if (firstTerm == null) {
            return null;
        }
        RetrievedDocTermInfo secondTerm = this.termStats.get(qTerms[1]);
        if (secondTerm == null) {
            return null;
        }
        tInfo = new RetrievedDocTermInfo(wv);
        tInfo.tf = firstTerm.tf * secondTerm.tf;
        
        return tInfo;
    }
    
    public void normalizefunction(TermsEnum termsEnum, PerDocTermVector docTermVector, float sim, int rank, String rlmMode, HashMap<String, WordVec> wordVecMap) throws IOException {
        BytesRef term;
        String termText;
        RetrievedDocTermInfo trmInfo;
        int tf;
        while ((term = termsEnum.next()) != null) { // explore the terms for this field
            termText = term.utf8ToString();
            tf = (int) termsEnum.totalTermFreq();

            // per-doc
            docTermVector.perDocStats.put(termText, new RetrievedDocTermInfo(termText, tf));
            docTermVector.sum_tf += tf;
            
            if (rank >= numTopDocs) {
                continue;
            }

            // collection stats for top k docs
            trmInfo = termStats.get(termText);
            if (trmInfo == null) {
                if (rlmMode.equals("bi")) {
                    trmInfo = new RetrievedDocTermInfo(termText, wordVecMap);
                } else {
                    trmInfo = new RetrievedDocTermInfo(termText);
                }
                
                termStats.put(termText, trmInfo);
            }
            trmInfo.tf += tf;
            trmInfo.df++;
            sumTf += tf;
            sumSim += sim;
        }
        
    }
    
    PerDocTermVector buildStatsForSingleDoc(int docId, int rank, float sim, String rlmMode, HashMap<String, WordVec> wordVecMap) throws Exception {
        String termText;
        BytesRef term;
        Terms tfvector;
        TermsEnum termsEnum;
        int tf;
        RetrievedDocTermInfo trmInfo;
        PerDocTermVector docTermVector = new PerDocTermVector(docId);
        docTermVector.sim = sim;  // sim value for document D_j

        tfvector = reader.getTermVector(docId, TrecDocIndexer.ALL_STR);
        if (tfvector != null && tfvector.size() != 0) {
            termsEnum = tfvector.iterator(); // access the terms for this field
            normalizefunction(termsEnum, docTermVector, sim, rank, rlmMode, wordVecMap);
        } else {
            String words = reader.document(docId).get(TrecDocIndexer.ALL_STR);
            computePerDocStatWithoutTfVector(words, docTermVector, rank, rlmMode, wordVecMap, sim);       
        }
        
        return docTermVector;
    }
    
    public void computePerDocStatWithoutTfVector(String words, PerDocTermVector docTermVector, int rank, String rlmMode, HashMap<String,WordVec> wordVecMap, double sim) throws Exception {
        
        TRECQueryParser tqp = new TRECQueryParser();
        String wordArray[] = tqp.analyze(words, "stop.txt").split("\\s+");
        HashMap<String, Integer> wordMap = new HashMap<>();
        for (String word : wordArray) {
            if (wordMap.containsKey(word)) {
                wordMap.put(word, wordMap.get(word) + 1);
            } else {
                wordMap.put(word, 1);
            }
        }
        RetrievedDocTermInfo trmInfo;
        Iterator it = wordMap.keySet().iterator();
        while (it.hasNext()) {
            String word = (String) it.next();
            docTermVector.perDocStats.put(word, new RetrievedDocTermInfo(word, wordMap.get(word)));
            docTermVector.sum_tf += wordMap.get(word);
            
            if (rank >= numTopDocs) {
                continue;
            }

            // collection stats for top k docs
            trmInfo = termStats.get(word);
            if (trmInfo == null) {
                if (rlmMode.equals("bi")) {
                    trmInfo = new RetrievedDocTermInfo(word, wordVecMap);
                } else {
                    trmInfo = new RetrievedDocTermInfo(word);
                }
                
                termStats.put(word, trmInfo);
            }
            trmInfo.tf += wordMap.get(word);
            trmInfo.df++;
            sumTf += wordMap.get(word);
            sumSim += sim;
        }
        
    }
}
