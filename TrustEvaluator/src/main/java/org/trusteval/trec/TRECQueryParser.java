/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.trec;

/**
 *
 * @author Debasis
 */
import java.io.FileReader;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;
import java.util.*;
import java.io.*;
import java.io.FileNotFoundException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.trusteval.indexing.TrecDocIndexer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class TRECQueryParser extends DefaultHandler {

    StringBuffer buff;      // Accumulation buffer for storing the current topic
    String fileName;
    QueryObject  query;
    Analyzer analyzer;
    StandardQueryParser queryParser;
    ArrayList<String> expansionTerms;
    boolean expansionflag;
    public List<QueryObject> queries;
    final static String[] tags = {"id", "title", "desc", "narr"};
    String fieldName;
    
    
    public TRECQueryParser(){
    
    
    }

    public TRECQueryParser(String fileName, Analyzer analyzer, boolean expansionFlag, String fieldName) throws SAXException, FileNotFoundException, Exception {
        this.fileName = fileName;
        this.analyzer = analyzer;
        buff = new StringBuffer();
        queries = new LinkedList<>();
        queryParser = new StandardQueryParser(analyzer);
        this.expansionflag = expansionFlag;
        this.fieldName = fieldName;
        if (this.expansionflag == true) {
            expansionTerms = extractExpansionTerms();
        }
    }

    public StandardQueryParser getQueryParser() {
        return queryParser;
    }

    public void parse() throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setValidating(false);
        SAXParser saxParser = saxParserFactory.newSAXParser();
        saxParser.parse(fileName, this);
    }

    public List<QueryObject> getQueries() {
        return queries;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        try {
            if (qName.equalsIgnoreCase("top")) {
                query = new QueryObject();
                queries.add(query);
            } else {
                buff = new StringBuffer();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public Query constructFlatQuery(QueryObject  trecQuery) {

        String queryText = "";
        String st[] = trecQuery.title.split("\\s+");
        for (String s : st) {
            queryText = queryText + " " + s;
        }

        st = trecQuery.desc.split("\\s+");
        for (String s : st) {
            queryText = queryText + " " + s;
        }

        st = trecQuery.narr.split("\\s+");
        for (String s : st) {
            queryText = queryText + " " + s;
        }

        st = queryText.split("\\s+");
        BooleanQuery query = new BooleanQuery();
        for (String s : st) {
            Term term1 = new Term(fieldName, s);
            //create the term query object
            Query query1 = new TermQuery(term1);
            //query1.setBoost(1.2f);
            query.add(query1, BooleanClause.Occur.SHOULD);
        }

        return query;
    }

  
    public Query constructLuceneQueryObj(QueryObject  trecQuery) throws QueryNodeException {
        
            return constructFlatQuery(trecQuery);
    }

    public String analyze(String query, String stopFileName) throws Exception {
        StringBuffer buff = new StringBuffer();
        TokenStream stream = new EnglishAnalyzer(StopFilter.makeStopSet(buildStopwordList(stopFileName))).tokenStream("dummy", new StringReader(query));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            term = term.toLowerCase();
            buff.append(term).append(" ");
        }
        stream.end();
        stream.close();
        return buff.toString().trim();
    }

    public List<String> buildStopwordList(String stopwordFileName) throws FileNotFoundException, IOException {
        List<String> stopwords = new ArrayList();
        String stopFile = stopwordFileName;
        String line;

        FileReader fr = new FileReader(stopFile);
        BufferedReader br = new BufferedReader(fr);
        while ((line = br.readLine()) != null) {
            stopwords.add(line.trim());
        }
        br.close();
        return stopwords;
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            if (qName.equalsIgnoreCase("title")) {
                query.title = analyze(buff.toString(), "stop.txt");
            } else if (qName.equalsIgnoreCase("desc")) {
                query.desc = analyze(buff.toString(), "stop.txt");
            } else if (qName.equalsIgnoreCase("narr")) {
                query.narr = analyze(buff.toString(), "stop.txt");
            } else if (qName.equalsIgnoreCase("num")) {
                query.id = buff.toString();
            } else if (qName.equalsIgnoreCase("top")) {
                query.luceneQuery = constructLuceneQueryObj(query);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void addExpansionTerms() throws QueryNodeException {

        ArrayList<Query> modifiedQueries = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            QueryObject  tq = queries.get(i);
            BooleanQuery b = (BooleanQuery) tq.luceneQuery;
            String expansionString = expansionTerms.get(i);
            String st[] = expansionString.split("\\s+");
            for (String s : st) {
                try {
                    //Term term1 = new Term(TrecDocIndexer.ABSTRACT_TEXT, s);
                    Term term1 = new Term(TrecDocIndexer.ALL_STR, s);

                    //create the term query object
                    Query query1 = new TermQuery(term1);
                   // if (weighted.equals("true")) {
                        query1.setBoost(0.05f);
                    //}
                    b.add(query1, BooleanClause.Occur.SHOULD);

                } catch (Exception e) {
                }
            }
            tq.luceneQuery = b;

        }
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        buff.append(new String(ch, start, length));
    }

    public ArrayList<String> extractExpansionTerms() throws FileNotFoundException, IOException, Exception {

        FileReader fr = new FileReader(new File("Topics_2018_gene_exp(Created).csv"));
        BufferedReader br = new BufferedReader(fr);

        ArrayList<String> geneexpansion = new ArrayList<>();
        String line = br.readLine();
        line = br.readLine();
        while (line != null) {
            String st[] = line.split(",");
            String expansionTerms = "";
            for (int i = 1; i < st.length - 2; i++) {
                String text = analyze(st[i], "stop.txt");
                String s1[] = text.split("\\s+");
                if (s1.length == 1) {
                    expansionTerms = expansionTerms + " " + text;
                }
            }
            geneexpansion.add(expansionTerms);
            line = br.readLine();
        }
        return geneexpansion;
    }

    public ArrayList<QueryObject> findQueries(String fileName, Properties prop) throws FileNotFoundException, IOException, Exception {
        FileReader fr = new FileReader(new File(fileName));
        BufferedReader br = new BufferedReader(fr);
        String line = br.readLine();
        ArrayList<QueryObject> tqs = new ArrayList<>();
        while (line != null) {
            String st[] = line.split(";");
            String query = analyze(st[1], "stop.txt");
            QueryObject  tq = new QueryObject ();
            tq.id = st[0];
            st = query.split("\\s+");
            BooleanQuery bq = new BooleanQuery();
            for (String s : st) {
                Term term1 = new Term(fieldName, s);
                //create the term query object
                Query query1 = new TermQuery(term1);
                //query1.setBoost(1.2f);
                bq.add(query1, BooleanClause.Occur.SHOULD);
            }

            tq.luceneQuery = bq;
            
            tqs.add(tq);
            line = br.readLine();
        }
        return tqs;
    }
    
    public ArrayList<QueryObject> loadMSMarcoQueries(String fileName, Properties prop) throws FileNotFoundException, IOException, Exception {
        FileReader fr = new FileReader(new File(fileName));
        BufferedReader br = new BufferedReader(fr);
        String line = br.readLine();
        ArrayList<QueryObject> tqs = new ArrayList<>();
        int count = 0;
        while (line != null) {
            String st[] = line.split(",");
            String query = analyze(st[0], "stop.txt");
            QueryObject  tq = new QueryObject ();
            tq.id = String.valueOf(count++);
            st = query.split("\\s+");
            BooleanQuery bq = new BooleanQuery();
            for (String s : st) {
                Term term1 = new Term(fieldName, s);
                //create the term query object
                Query query1 = new TermQuery(term1);
                //query1.setBoost(1.2f);
                bq.add(query1, BooleanClause.Occur.SHOULD);
            }

            tq.luceneQuery = bq;
            
            tqs.add(tq);
            
            query = analyze(st[2], "stop.txt");
            tq = new QueryObject ();
            tq.id = String.valueOf(count++);
            st = query.split("\\s+");
            bq = new BooleanQuery();
            for (String s : st) {
                Term term1 = new Term(fieldName, s);
                //create the term query object
                Query query1 = new TermQuery(term1);
                //query1.setBoost(1.2f);
                bq.add(query1, BooleanClause.Occur.SHOULD);
            }

            tq.luceneQuery = bq;
            
            tqs.add(tq);
            line = br.readLine();
        }
        return tqs;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }

        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));
            String queryFile = prop.getProperty("query.file");

            TRECQueryParser parser = new TRECQueryParser(queryFile, new EnglishAnalyzer(), true, prop.getProperty("fieldName"));
            parser.extractExpansionTerms();
            /* parser.parse();
            for (Query q : parser.queries) {
                System.out.println(q);
            }*/
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
