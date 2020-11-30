/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.evaluator;

import java.io.BufferedReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.lucene.index.*;
import org.trusteval.trec.TRECQueryParser;

/**
 *
 * @author Debasis
 */
class QueryPair {

    String query1;
    String query2;
    String label;

    public QueryPair(String q1, String q2, String label) {
        query1 = q1;
        query2 = q2;
        this.label = label;
    }

}

class PerQueryRelDocs {

    String qid;
    HashMap<String, Integer> relMap; // keyed by docid, entry stores the rel value
    int numRel;

    public PerQueryRelDocs(String qid) {
        this.qid = qid;
        numRel = 0;
        relMap = new HashMap<>();
    }

    void addTuple(String docId, int rel) {
        if (relMap.get(docId) != null) {
            return;
        }
        if (rel > 0) {
            numRel++;
            relMap.put(docId, rel);
        }
    }
}

class AllRelRcds {

    String qrelsFile;
    HashMap<String, PerQueryRelDocs> perQueryRels;
    int totalNumRel;

    public AllRelRcds(String qrelsFile) {
        this.qrelsFile = qrelsFile;
        perQueryRels = new HashMap<>();
        totalNumRel = 0;
    }

    int getTotalNumRel() {
        //if (totalNumRel > 0)
        //return totalNumRel;
        totalNumRel = 0;
        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            totalNumRel += perQryRelDocs.numRel;
        }
        return totalNumRel;
    }

    void load() throws Exception {
        FileReader fr = new FileReader(qrelsFile);
        BufferedReader br = new BufferedReader(fr);
        String line;

        while ((line = br.readLine()) != null) {
            storeRelRcd(line);
        }
        br.close();
        fr.close();
    }

    void storeRelRcd(String line) {
        String[] tokens = line.split("\\s+");
        String qid = tokens[0];
        PerQueryRelDocs relTuple = perQueryRels.get(qid);
        if (relTuple == null) {
            relTuple = new PerQueryRelDocs(qid);
            perQueryRels.put(qid, relTuple);
        }
        relTuple.addTuple(tokens[2], Integer.parseInt(tokens[3]));
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, PerQueryRelDocs> e : perQueryRels.entrySet()) {
            PerQueryRelDocs perQryRelDocs = e.getValue();
            buff.append(e.getKey()).append("\n");
            for (Map.Entry<String, Integer> rel : perQryRelDocs.relMap.entrySet()) {
                String docName = rel.getKey();
                int relVal = rel.getValue();
                buff.append(docName).append(",").append(relVal).append("\t");
            }
            buff.append("\n");
        }
        return buff.toString();
    }

    PerQueryRelDocs getRelInfo(String qid) {
        return perQueryRels.get(qid);
    }
}

class ResultTuple implements Comparable<ResultTuple> {

    String docName; // doc name
    int rank;       // rank of retrieved document
    int rel;    // is this relevant? comes from qrel-info
    String content;

    public ResultTuple(String docName, int rank) {
        this.docName = docName;
        this.rank = rank;
    }

    public ResultTuple(String docName, int rank, String content) {
        this.docName = docName;
        this.rank = rank;
        this.content = content;
    }

    @Override
    public int compareTo(ResultTuple t) {
        return rank < t.rank ? -1 : rank == t.rank ? 0 : 1;
    }
}

class RetrievedResults implements Comparable<RetrievedResults> {

    String qid;
    List<ResultTuple> rtuples;
    int numRelRet;
    float avgP;
    PerQueryRelDocs relInfo;
    IndexReader reader;
    Boolean debugMode;

    public RetrievedResults(String qid, String indexPath, Boolean debugMode) {
        this.qid = qid;
        this.rtuples = new ArrayList<>(1000);
        avgP = -1;
        numRelRet = -1;
        this.debugMode = debugMode;
    }

    void addTuple(String docName, int rank, String content, String evalMode) {
        if (evalMode.equals("trust")) {
            rtuples.add(new ResultTuple(docName, rank, content));
        } else {
            rtuples.add(new ResultTuple(docName, rank));
        }
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (ResultTuple rt : rtuples) {
            buff.append(qid).append("\t").
                    append(rt.docName).append("\t").
                    append(rt.rank).append("\t").
                    append(rt.rel).append("\n");
        }
        return buff.toString();
    }

    void fillRelInfo(PerQueryRelDocs relInfo) {
        String qid = relInfo.qid;

        for (ResultTuple rt : rtuples) {
            Integer relIntObj = relInfo.relMap.get(rt.docName);
            rt.rel = relIntObj == null ? 0 : relIntObj.intValue();
        }
        this.relInfo = relInfo;
    }

    float computeAP() {
        if (avgP > -1) {
            return avgP;
        }

        float prec = 0;

        int numRel = 0;
        if (relInfo != null) {
            numRel = relInfo.numRel;
        }
        int numRelSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel < Evaluator.threshold) {
                continue;
            }
            numRelSeen++;
            prec += numRelSeen / (float) (tuple.rank);
        }
        numRelRet = numRelSeen;
        prec = numRel == 0 ? 0 : prec / (float) numRel;
        this.avgP = prec;

        return prec;
    }

    float computeDCG(List<ResultTuple> rtuples, int cutoff) {
        float dcgSum = 0;
        int count = 1;
        for (ResultTuple tuple : rtuples) {
            int twoPowerRel = 1 << tuple.rel;
            float dcg = (twoPowerRel - 1) / (float) (Math.log(count + 1) / Math.log(2));
            dcgSum += dcg;
            if (count >= cutoff) {
                break;
            }
            count++;
        }
        return dcgSum;
    }

    float computeNDCG(int ntops) {
        float dcg = 0, idcg = 0;
        List<ResultTuple> idealTuples = new ArrayList<>(rtuples);
        Collections.sort(idealTuples, new Comparator<ResultTuple>() {
            @Override
            public int compare(ResultTuple thisObj, ResultTuple thatObj) { // descending in rel values
                return thisObj.rel > thatObj.rel ? -1 : thisObj.rel == thatObj.rel ? 0 : 1;
            }
        });

        dcg = computeDCG(this.rtuples, ntops);
        idcg = computeDCG(idealTuples, ntops);

        return idcg > 0 ? dcg / idcg : 0;
    }

    float precAtTop(int k) {
        int numRelSeen = 0;
        int numSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel >= 1) {
                numRelSeen++;
            }
            if (++numSeen >= k) {
                break;
            }
        }
        return numRelSeen / (float) k;
    }

    float computeRecall() {
        if (numRelRet > -1) {
            return numRelRet;
        }
        int numRelSeen = 0;
        for (ResultTuple tuple : this.rtuples) {
            if (tuple.rel < 1) {
                continue;
            }
            numRelSeen++;
        }
        numRelRet = numRelSeen;
        return numRelSeen;
    }

    double computeJaccard(RetrievedResults rtuple2) {

        ArrayList<String> docIds = new ArrayList<>();
        ArrayList<String> docIds2 = new ArrayList<>();
        for (int i = 0; i < rtuples.size(); i++) {
            String docid = rtuples.get(i).docName;
            docIds.add(docid);
        }
        for (int i = 0; i < rtuple2.rtuples.size(); i++) {
            String docid = rtuple2.rtuples.get(i).docName;
            docIds2.add(docid);
        }

        int numOverLap = 0;
        for (String id : docIds) {
            if (docIds2.contains(id)) {
                numOverLap++;
            }
        }

        if (debugMode) {
            System.out.println(docIds);
            System.out.println(docIds2);
            System.out.println("number of overlap " + numOverLap);
        }

        double jacc = (double) numOverLap / (docIds.size() + docIds2.size() - numOverLap);

        if (debugMode) {
            System.out.println("Jaccard " + jacc);

        }
        return jacc;
    }

    double computeInverseJaccard(RetrievedResults rtuple2) {

        return (1 - computeJaccard(rtuple2));
    }

    public HashMap<String, Double> computeWordDistribution(ArrayList<String> contentArray) {

        HashMap<String, Double> wordCountMap = new HashMap<String, Double>();
        double totalSumFreq = 0;
        for (int i = 0; i < contentArray.size(); i++) {
            String content = contentArray.get(i);
            String words[] = content.split("\\s+");
            totalSumFreq += words.length;
            for (String word : words) {
                if (wordCountMap.containsKey(word)) {
                    wordCountMap.put(word, wordCountMap.get(word) + 1);
                } else {
                    wordCountMap.put(word, 1.0);
                }
            }
        }

        Iterator it = wordCountMap.keySet().iterator();
        double n = wordCountMap.size();

        while (it.hasNext()) {
            String st = (String) it.next();
            wordCountMap.put(st, wordCountMap.get(st) / totalSumFreq);
        }

        if (debugMode) {
            System.out.println(wordCountMap.size());
        }
        return wordCountMap;
    }

    public double computeKLDivergence(HashMap<String, Double> wordCountMap1, HashMap<String, Double> wordCountMap2) {

        Iterator it = wordCountMap1.keySet().iterator();
        double kldiv = 0;

        while (it.hasNext()) {
            String key = (String) it.next();
            if (wordCountMap2.containsKey(key)) {
                kldiv += wordCountMap1.get(key) * Math.log(wordCountMap1.get(key) / wordCountMap2.get(key));
                //
            }
        }
        return kldiv;
    }

    double computeOverlap(RetrievedResults rtuple2) throws Exception {

        ArrayList<String> contentArray = new ArrayList<>();
        TRECQueryParser tqp = new TRECQueryParser();
        for (int i = 0; i < rtuples.size(); i++) {
            String docid = rtuples.get(i).docName;
            String content = rtuples.get(i).content;
            content = tqp.analyze(content, "stop.txt");
            contentArray.add(content);
        }
        HashMap<String, Double> wordCountMap1 = computeWordDistribution(contentArray);
        //System.out.println(wordCountMap1);

        contentArray = new ArrayList<>();
        for (int i = 0; i < rtuple2.rtuples.size(); i++) {
            String docid = rtuple2.rtuples.get(i).docName;
            String content = rtuple2.rtuples.get(i).content;
            contentArray.add(content);
        }
        HashMap<String, Double> wordCountMap2 = computeWordDistribution(contentArray);
        //System.out.println(wordCountMap1);
        double kldiv = computeKLDivergence(wordCountMap1, wordCountMap2);
        return kldiv;
    }

    public double computeInverseOverlap(RetrievedResults rtuple2) throws Exception {

        return (1 - computeOverlap(rtuple2));

    }

    double computeWeightedJaccard(RetrievedResults rtuple2) {

        ArrayList<String> docIds = new ArrayList<>();
        ArrayList<String> docIds2 = new ArrayList<>();
        ArrayList<String> docIdUnion = new ArrayList<>();
        HashSet<String> docNames = new HashSet<String>();
        for (int i = 0; i < rtuples.size(); i++) {
            String docid = rtuples.get(i).docName;
            docIds.add(docid);
            if (!docNames.contains(docid)) {
                docIdUnion.add(docid);
                docNames.add(docid);
            }
        }
        for (int i = 0; i < rtuple2.rtuples.size(); i++) {
            String docid = rtuple2.rtuples.get(i).docName;
            docIds2.add(docid);
            if (!docNames.contains(docid)) {
                docIdUnion.add(docid);
                docNames.add(docid);
            }
        }
        double wJacc = 0;
        for (int i = 0; i < docIdUnion.size(); i++) {
            String docId = docIdUnion.get(i);
            int rank1 = docIds.indexOf(docId) + 1;
            int rank2 = docIds2.indexOf(docId) + 1;
            if (rank1 == 0) {
                rank1 = 50;
            }
            if (rank2 == 0) {
                rank1 = 50;
            }
            wJacc += Math.exp(-((rank1 - rank2) * (rank1 - rank2)));
        }
        wJacc /= docIdUnion.size();
        System.out.println(wJacc);
        return wJacc;
    }

    public double computeInverseWeightedJaccard(RetrievedResults rtuple2) {

        return (1 - computeWeightedJaccard(rtuple2));
    }

    @Override
    public int compareTo(RetrievedResults that) {
        return this.qid.compareTo(that.qid);
    }
}

class AllRetrievedResults {

    Map<String, RetrievedResults> allRetMap;
    String resFile;
    AllRelRcds allRelInfo;
    IndexReader reader;

    public AllRetrievedResults(String resFile) {
        this.resFile = resFile;
        allRetMap = new TreeMap<>();
    }

    public void load(String evalMode, Boolean debugMode) {
        String line;
        try (FileReader fr = new FileReader(resFile);
                BufferedReader br = new BufferedReader(fr);) {
            while ((line = br.readLine()) != null) {
                storeRetRcd(line, evalMode, debugMode);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void storeRetRcd(String line, String evalMode, Boolean debugMode) {
        String[] tokens = line.split("\t");
        String qid = tokens[0];
        RetrievedResults res = allRetMap.get(qid);
        if (res == null) {
            res = new RetrievedResults(qid, "", debugMode);
            allRetMap.put(qid, res);
        }
        res.addTuple(tokens[2], Integer.parseInt(tokens[3]), tokens[6], evalMode);
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            buff.append(res.toString()).append("\n");
        }
        return buff.toString();
    }

    public void fillRelInfo(AllRelRcds relInfo, String evalMode) {
        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            PerQueryRelDocs thisRelInfo = null;
            
            if (evalMode.equals("trust")) {
                thisRelInfo = relInfo.getRelInfo(String.valueOf(res.qid));
            } else {
                String qid_new = String.valueOf(res.qid).split("-")[0];
                thisRelInfo = relInfo.getRelInfo(qid_new);
            }

            if (thisRelInfo != null) {
                res.fillRelInfo(thisRelInfo);
            }else{
                System.out.println("Qid Not Found "+res.qid);
            }
        }
        this.allRelInfo = relInfo;
    }

    public String computeTrustMetrcs(ArrayList<QueryPair> queryPairs) {
        StringBuffer buff = new StringBuffer();
        double avgJaccard = 0;
        double avgWeightedJaccard = 0;
        double contentMetric = 0;
        for (int i = 0; i < queryPairs.size(); i++) {
            String q1 = queryPairs.get(i).query1;
            String q2 = queryPairs.get(i).query2;
            String label = queryPairs.get(i).label;
            RetrievedResults r1 = allRetMap.get(q1);
            RetrievedResults r2 = allRetMap.get(q2);
            try {
                if (label.equals("1")) {
                    avgJaccard += r1.computeJaccard(r2);
                    avgWeightedJaccard += r1.computeWeightedJaccard(r2);
                    contentMetric += r1.computeOverlap(r2);
                } else {
                    avgJaccard += r1.computeInverseJaccard(r2);
                    avgWeightedJaccard += r1.computeInverseWeightedJaccard(r2);
                    contentMetric += r1.computeInverseOverlap(r2);
                }

            } catch (Exception e) {
                System.out.println("Exception..");
                e.printStackTrace();
                System.out.println(allRetMap.containsKey(q1) + " " + allRetMap.containsKey(q2) + q1 + " " + q2);
            }
        }

        buff.append("Avg Jaccard:\t").append(avgJaccard / (double) queryPairs.size()).append("\n");
        buff.append("Avg Weighted Jaccard:\t").append(avgWeightedJaccard / (double) queryPairs.size()).append("\n");
        buff.append("Avg Content Sim:\t").append(contentMetric / (double) queryPairs.size()).append("\n");

        return buff.toString();
    }

    String computeAll() {
        StringBuffer buff = new StringBuffer();
        float map = 0f;
        float gm_ap = 0f;
        float avgRecall = 0f;
        float numQueries = (float) allRetMap.size();
        float pAt5 = 0f;
        float ndcg = 0;
        float ndcg_5 = 0;

        for (Map.Entry<String, RetrievedResults> e : allRetMap.entrySet()) {
            RetrievedResults res = e.getValue();
            float ap = res.computeAP();
            map += ap;
            gm_ap += ap > 0 ? Math.log(ap) : 0;
            avgRecall += res.computeRecall();
            pAt5 += res.precAtTop(5);
            if (Evaluator.graded) {
                float thisNDCG = res.computeNDCG(res.rtuples.size());
                float thisNDCG_5 = res.computeNDCG(5);
                ndcg += thisNDCG;
                ndcg_5 = thisNDCG_5;
            }
        }
        float f = avgRecall / (float) allRelInfo.getTotalNumRel();
        System.out.println("recall values " + avgRecall + " " + f);
        buff.append("recall:\t").append(avgRecall / 5588).append("\n");
        buff.append("map:\t").append(map / numQueries).append("\n");
        buff.append("gmap:\t").append((float) Math.exp(gm_ap / numQueries)).append("\n");
        buff.append("P@5:\t").append(pAt5 / numQueries).append("\n");
        if (Evaluator.graded) {
            buff.append("nDCG:\t").append(ndcg / numQueries).append("\n");
            buff.append("nDCG@5:\t").append(ndcg_5 / numQueries).append("\n");
        }

        return buff.toString();
    }
}

public class Evaluator {

    AllRelRcds relRcds;
    AllRetrievedResults retRcds;
    static boolean graded;
    static int threshold;
    ArrayList<QueryPair> queryPairs;
    String queryPairFile;

    public Evaluator(String qrelsFile, String resFile) {
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        graded = true;
        threshold = 1;
    }

    public Evaluator(Properties prop) {
        String qrelsFile = prop.getProperty("qrels.file");
        String resFile = prop.getProperty("res.file");
        graded = Boolean.parseBoolean(prop.getProperty("evaluate.graded", "false"));
        if (graded) {
            threshold = Integer.parseInt(prop.getProperty("evaluate.graded_to_bin.threshold", "1"));
        } else {
            threshold = 1;
        }
        relRcds = new AllRelRcds(qrelsFile);
        retRcds = new AllRetrievedResults(resFile);
        queryPairFile = prop.getProperty("querypairs.file");
    }

    public void load(String evalMode, Boolean debugMode) throws Exception {
        relRcds.load();
        retRcds.load(evalMode, debugMode);
    }

    public void fillRelInfo(String evalMode) {
        retRcds.fillRelInfo(relRcds, evalMode);
    }

    public void loadQueryPairsTREC() throws IOException {
        FileReader fr = new FileReader(new File(queryPairFile));
        BufferedReader br = new BufferedReader(fr);

        String line = br.readLine();
        String prevLine = line;
        double avgLength = 0;
        int lineCount = 0;

        this.queryPairs = new ArrayList<>();

        HashMap<String, ArrayList<String>> idMap = new HashMap<>();
        while (line != null) {
            String st[] = line.split(";");
            String tokens[] = st[0].split("-");
            String id = tokens[0] + "-" + tokens[1];
            ArrayList<String> ids = new ArrayList<>();
            if (idMap.containsKey(id)) {
                ids = idMap.get(id);
            }
            ids.add(st[0]);
            idMap.put(id, ids);
            avgLength += tokens.length;
            line = br.readLine();
        }
        Iterator it = idMap.keySet().iterator();
        while (it.hasNext()) {
            String st = (String) it.next();
            ArrayList<String> ids = idMap.get(st);
            for (int i = 0; i < ids.size() - 1; i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    String pair1 = ids.get(i);
                    String pair2 = ids.get(j);
                    String pair = pair1 + "#" + pair2;
                    QueryPair qp = new QueryPair(pair1, pair2, "0");
                    queryPairs.add(qp);
                }
            }
        }
    }

    public void loadQueryPairsMSMARCO() throws IOException {
        FileReader fr = new FileReader(new File(queryPairFile));
        BufferedReader br = new BufferedReader(fr);

        String line = br.readLine();
        String prevLine = line;
        double avgLength = 0;
        int lineCount = 0;

        this.queryPairs = new ArrayList<>();
        int count = 0;
        HashMap<String, ArrayList<String>> idMap = new HashMap<>();
        while (line != null) {
            String st[] = line.split("\t");
            String pair1 = st[0];
            String pair2 = st[2];
            QueryPair qp = new QueryPair(pair1, pair2, st[4]);
            queryPairs.add(qp);
            line = br.readLine();
        }
    }

    public String computeAll() {
        return retRcds.computeAll();
    }

    public String computeTrust() {
        return retRcds.computeTrustMetrcs(queryPairs);
    }

    @Override
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append(relRcds.toString()).append("\n");
        buff.append(retRcds.toString());
        return buff.toString();
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "init.properties";
        }
        try {
            Properties prop = new Properties();
            prop.load(new FileReader(args[0]));

            String qrelsFile = prop.getProperty("qrels.file");
            String resFile = prop.getProperty("res.file");

            Evaluator evaluator = new Evaluator(qrelsFile, resFile);
            evaluator.load("trust", null);
            evaluator.fillRelInfo("trust");
            System.out.println(evaluator.computeAll());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}
