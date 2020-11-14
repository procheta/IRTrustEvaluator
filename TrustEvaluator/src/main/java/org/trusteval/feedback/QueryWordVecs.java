/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.feedback;

import java.util.ArrayList;
import java.util.List;
import org.trusteval.trec.QueryObject;
import org.trusteval.wvec.WordVec;
import org.trusteval.wvec.WordVecs;

/**
 *
 * @author Debasis
 */
public class QueryWordVecs {
    QueryObject  query;
    List<WordVec> wvecs;
    WordVecs allwvecs;
    
    public QueryWordVecs(QueryObject  query, WordVecs allwVecs) {
        this.query = query;
        this.allwvecs = allwVecs;
        wvecs = new ArrayList<>();
    }
    
    void addQueryWordVec(String word) {
        WordVec wv = allwvecs.getVec(word);
        wvecs.add(wv);
    }
    
    void addQueryWordVec(WordVec wv) {
        wvecs.add(wv);
    }
    
    List<WordVec> getVecs() { return wvecs; }    
}