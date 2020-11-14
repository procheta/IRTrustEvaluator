/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.trec;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;

/**
 *
 * @author Debasis
 */
public class QueryObject {
    public String       id;
    public String       title;
    public String       desc;
    public String       narr;
    public Query        luceneQuery;
    
    @Override
    public String toString() {
        return luceneQuery.toString();
    }

    public QueryObject() {}
    
    public QueryObject(QueryObject that) { // copy constructor
        this.id = that.id;
        this.title = that.title;
        this.desc = that.desc;
        this.narr = that.narr;
    }

    public Query getLuceneQueryObj() { return luceneQuery; }

    public Set<Term> getQueryTerms() {
        Set<Term> terms = new HashSet<>();
      //  luceneQuery.extractTerms(terms);
        return terms;
    }
}
