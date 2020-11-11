/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.feedback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.trusteval.indexing.TrecDocIndexer;
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

    public void buildAllStats(String retrieveMode) throws Exception {
        int rank = 0;
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            int docId = scoreDoc.doc;
            docTermVecs.add(buildStatsForSingleDoc(docId, rank, scoreDoc.score, retrieveMode));
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

    public void normalizefunction(TermsEnum termsEnum, PerDocTermVector docTermVector, float sim, int rank) throws IOException {
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
                trmInfo = new RetrievedDocTermInfo(termText);
                termStats.put(termText, trmInfo);
            }
            trmInfo.tf += tf;
            trmInfo.df++;
            sumTf += tf;
            sumSim += sim;
        }

    }

    PerDocTermVector buildStatsForSingleDoc(int docId, int rank, float sim, String queryMode) throws Exception {
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
                normalizefunction(termsEnum, docTermVector, sim, rank);
            }

        return docTermVector;
    }
}
