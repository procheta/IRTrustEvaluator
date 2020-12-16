/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.trec;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 *
 * @author dwaipayan
 */
public class MsMarcoQueryParser {
    StringBuffer buff;      // Accumulation buffer for storing the current topic
    String queryFile;
    QueryObject  query;
    Analyzer analyzer;

    public MsMarcoQueryParser(String fileName, Analyzer analyzer) {
        this.queryFile = fileName;
        this.analyzer = analyzer;
    }

    public List<QueryObject> makeQuery() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(queryFile)));
        List<QueryObject> queries = new ArrayList<>();

        String line = null;
        while ((line = br.readLine()) != null) {
            String tokens[] = line.split("\t");
            QueryObject qo = new QueryObject(tokens[0], tokens[1], "", "");

            String analyzedTitle = analyze(qo);

            qo.title = analyzedTitle;

            qo.luceneQuery = makeLuceneQuery(qo, "words");
            queries.add(qo);
        }
        return queries;
    }

    public String analyze(QueryObject qo) throws IOException {
        
        StringBuffer tokenizedContentBuff = new StringBuffer();
        TokenStream stream = analyzer.tokenStream("dummy", new StringReader(qo.title));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);

        stream.reset();

        while (stream.incrementToken()) {
            String term = termAtt.toString();
            tokenizedContentBuff.append(term).append(" ");
        }

        stream.end();
        stream.close();

        return tokenizedContentBuff.toString();
    }

    public Query makeLuceneQuery(QueryObject qo, String fieldName) throws IOException {
        StringBuffer tokenizedContentBuff = new StringBuffer();

        BooleanQuery query = new BooleanQuery();
        for (String s : qo.title.split(" ")) {
            Term term1 = new Term(fieldName, s);
            //create the term query object
            Query query1 = new TermQuery(term1);
            //query1.setBoost(1.2f);
            query.add(query1, BooleanClause.Occur.SHOULD);
        }
        return query;
    }
}
