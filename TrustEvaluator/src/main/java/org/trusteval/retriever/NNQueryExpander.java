/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.retriever;

import org.trusteval.indexing.TrecDocIndexer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.trusteval.trec.QueryObject;
import org.trusteval.wvec.WordVec;
import org.trusteval.wvec.WordVecs;

/**
 *
 * @author Debasis
 * Use vector compositions to form an expanded query. Most likely
 * will be useful for more effective density estimation.
 */

class NNQueryWord implements Comparable<NNQueryWord> {
    WordVec wvec;
    float avgSim;

    public NNQueryWord(WordVec wvec) {
        this.wvec = wvec;
    }
    
    @Override
    public int compareTo(NNQueryWord that) {
        return avgSim < that.avgSim? 1 : avgSim == that.avgSim? 0 : -1;
    }
}

public class NNQueryExpander {

    WordVecs wvecs;
    int numTerms;
    String weighted;
    
    public NNQueryExpander(TrecDocIndexer indexer) {
        try {
            wvecs = new WordVecs(indexer.getProperties());
            numTerms = Integer.parseInt(indexer.getProperties().getProperty("kde.queryexpansion"));
            weighted = indexer.getProperties().getProperty("weighted");
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    public NNQueryExpander(Properties prop) {
        try {
            System.out.println("Loading wordvecs in memory...");
            wvecs = new WordVecs(prop);
            System.out.println("Loaded wordvecs in memory...");
            numTerms = Integer.parseInt(prop.getProperty("rlm.qe.nterms"));
        }
        catch (Exception ex) { ex.printStackTrace(); }        
    }
    
    public NNQueryExpander(WordVecs wvecs, int numTerms) {
        this.wvecs = wvecs;
        this.numTerms = numTerms;
    }
    
    public void expandQuery(QueryObject  query) {
        // Collect the origTerms
        Query luceneQry = query.getLuceneQueryObj();
        System.out.println("Composing query: " + luceneQry.toString());
        HashSet<Term> origTerms = new HashSet<>();
        //luceneQry.extractTerms(origTerms);
        
        // For checking that we are adding new expansion terms
        HashSet<String> origTermStrings = new HashSet<>();
        for (Term t : origTerms) {
            origTermStrings.add(t.text());
        }
        
        Term[] termArray = new Term[origTerms.size()];
        termArray = origTerms.toArray(termArray);
        
        // Initialize a hashmap to store the nn origTerms
        HashMap<String, NNQueryWord> nnMap = new HashMap<>();
        // Iterate over the original origTerms to pairwise compose them
        for (int i = 0; i < termArray.length; i++) {
            String thisTerm = termArray[i].text();
            String nextTerm = termArray[i+1].text();
            WordVec thisTermVec = wvecs.getVec(thisTerm);
            /*WordVec nextTermVec = wvecs.getVec(nextTerm);
            if (thisTermVec == null || nextTermVec == null) {
                continue;
            }*/
            if (thisTermVec == null )
                continue;
            List<WordVec> nnvecs = wvecs.getNearestNeighbors(thisTermVec);
            
            int nnIndex = 0;
            //System.out.println("NNs of composed words: " + thisTermVec.getWord() + "+" + nextTermVec.getWord());
            for (WordVec nnvec : nnvecs) {
                String thisWord = nnvec.getWord();
               // System.out.println("NN (" + nnIndex + "): " + thisWord + " (" + nnvec.getQuerySim() + ")");
                if (origTermStrings.contains(thisWord))
                    continue;
                
                NNQueryWord nnqw = nnMap.get(thisWord);
                if (nnqw == null) {
                    nnqw = new NNQueryWord(nnvec);
                }
                nnqw.avgSim += nnvec.getQuerySim();
                nnMap.put(thisWord, nnqw);
                nnIndex++;
            }            
        }

        List<NNQueryWord> nnqws = new ArrayList<>(nnMap.size());
        for (Map.Entry<String, NNQueryWord> e : nnMap.entrySet()) {
            nnqws.add(e.getValue());
           // System.out.println("Total sim (" + e.getKey() + ") = " + e.getValue().avgSim);
        }
        Collections.sort(nnqws);
        nnqws = nnqws.subList(0, Math.min(nnqws.size(), numTerms));
        
        // Now append the origTerms of this hashmap to the original query
        for (NNQueryWord nnqw : nnqws) {
            TermQuery tq = new TermQuery(
                   new Term(TrecDocIndexer.ALL_STR, nnqw.wvec.getWord()));
            System.out.print(nnqw.wvec.getWord());
            if(weighted.equals("true")){
                tq.setBoost(0.5f);
            }
            ((BooleanQuery)luceneQry).add(tq, BooleanClause.Occur.SHOULD);
        }
        System.out.println();
    }
    
    public void expandQueriesWithNN(List<QueryObject > queries) {
        for (QueryObject  q : queries) {
            expandQuery(q);
        }
    }
}