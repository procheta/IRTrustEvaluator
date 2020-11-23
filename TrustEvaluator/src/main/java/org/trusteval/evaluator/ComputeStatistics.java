/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.evaluator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.trusteval.trec.QueryObject;

/**
 *
 * @author Procheta
 */
public class ComputeStatistics {

    public void computeStatistics() throws FileNotFoundException, IOException {
        FileReader fr = new FileReader(new File("C://Users//Procheta//Downloads/robust-uqv.txt"));
        BufferedReader br = new BufferedReader(fr);

        String line = br.readLine();

        double avgLength = 0;
        int lineCount = 0;
        while (line != null) {
            String st[] = line.split(";");
            String tokens[] = st[1].split("\\s+");
            avgLength += tokens.length;
            line = br.readLine();
            lineCount++;
        }
        avgLength /= lineCount;
        System.out.println("Avg number of words " + avgLength);
    }

    public void seperateLabels() throws IOException {

        FileReader fr = new FileReader(new File("C:/Users/Procheta/Downloads/IR-TRUSTABILITY-Results-summary.csv"));
        BufferedReader br = new BufferedReader(fr);
        String line = br.readLine();
        ArrayList<QueryObject> tqs = new ArrayList<>();
        FileWriter fw = new FileWriter(new File("C:/Users/Procheta/Downloads/positive.txt"));
        BufferedWriter bw = new BufferedWriter(fw);
        FileWriter fw1 = new FileWriter(new File("C:/Users/Procheta/Downloads/negative.txt"));
        BufferedWriter bw1 = new BufferedWriter(fw1);

        int count = 0;
        while (line != null) {
            String st[] = line.split(",");
            String query = st[0];
            query = st[2];
            int i = Integer.parseInt(st[5]);
            count++;
            if (st[5].equals("0")) {
                bw.write(st[0] + "\t" + st[2] + "\t" + st[5]);
                bw.newLine();
            } else {
                bw1.write(st[0] + "\t" + st[2] + "\t" + st[5]);
                bw1.newLine();
            }
            line = br.readLine();
        }
        bw.close();
        bw1.close();
    }

    public void computeqIDS() throws FileNotFoundException, IOException {
        FileReader fr = new FileReader(new File("C:\\Users\\Procheta\\Downloads\\queries.tar\\queries/queries.train.tsv"));
        BufferedReader br = new BufferedReader(fr);
        String line = br.readLine();
        ArrayList<QueryObject> tqs = new ArrayList<>();
        FileWriter fw = new FileWriter(new File("C:/Users/Procheta/Downloads/positiveQid.txt"), true);
        BufferedWriter bw = new BufferedWriter(fw);

        HashMap<String, String> qMap = new HashMap<>();
        while (line != null) {
            String st[] = line.split("\t");
            String qid = st[0];
            String query = st[1];
            qMap.put(query, qid);
            line = br.readLine();
        }

        fr = new FileReader(new File("C:/Users/Procheta/Downloads/negative.txt"));
        br = new BufferedReader(fr);
        line = br.readLine();
        while (line != null) {
            String st[] = line.split("\t");
            String q1 = st[0];
            String q2 = st[1];
            if (qMap.containsKey(q1)) {
                bw.write(String.valueOf(qMap.get(q1)) + "\t" + q1);
                bw.newLine();
            } else {
                char c = '"';
                // q1 = q1.replaceAll(String.valueOf(c), "");
                System.out.println("Not Found");
                System.out.println(q1);
            }
            if (qMap.containsKey(q2)) {
                bw.write(String.valueOf(qMap.get(q2)) + "\t" + q2);
                bw.newLine();
            } else {
                System.out.println("Not Found");
                System.out.println(q2);
            }
            line = br.readLine();
        }
        bw.close();

    }

    public void prepareModifiedMSMArco() throws FileNotFoundException, IOException {

        FileReader fr = new FileReader(new File("C:\\Users\\Procheta\\Downloads\\MSMARCOQID.txt"));
        BufferedReader br = new BufferedReader(fr);
        String line = br.readLine();
        ArrayList<QueryObject> tqs = new ArrayList<>();

        FileWriter fw = new FileWriter(new File("C:/Users/Procheta/Downloads/negativeQid.txt"));
        BufferedWriter bw = new BufferedWriter(fw);
        HashMap<String, String> qMap = new HashMap<>();

        while (line != null) {
            String st[] = line.split("\t");
            String qid = st[0];
            String query = st[1];
            qMap.put(query, qid);
            line = br.readLine();
        }

        fr = new FileReader(new File("C:/Users/Procheta/Downloads/negative.txt"));
        br = new BufferedReader(fr);
        line = br.readLine();
        while (line != null) {
            String st[] = line.split("\t");
            String q1 = st[0];
            String q2 = st[1];
            if (qMap.containsKey(q1) && qMap.containsKey(q2)) {
                bw.write(String.valueOf(qMap.get(q1)) + "\t" + q1 + "\t" + String.valueOf(qMap.get(q2)) + "\t" + q2 + "\t" + st[2]);
                bw.newLine();
            } else {
                char c = '"';
                // q1 = q1.replaceAll(String.valueOf(c), "");
                System.out.println("Not Found");
                System.out.println(q1);
            }
            line = br.readLine();
        }
        bw.close();

    }

    public void getUniqueIds() throws FileNotFoundException, IOException {
        FileReader fr = new FileReader(new File("C:\\Users\\Procheta\\Downloads\\result.txt_msmarco_one_label"));
        BufferedReader br = new BufferedReader(fr);
        String line = br.readLine();

        HashSet<String> qids = new HashSet<>();

        while (line != null) {
            String st[] = line.split("\t");
            qids.add(st[0]);
            line = br.readLine();
        }
        //  System.out.println("The number of qids "+qids);

        fr = new FileReader(new File("C:\\Users\\Procheta\\Downloads\\qrels.train.tsv"));
        br = new BufferedReader(fr);
        line = br.readLine();

        HashSet<String> qrel = new HashSet<>();

        while (line != null) {
            String st[] = line.split("\t");
            qrel.add(st[0]);
            line = br.readLine();
        }
        // System.out.println(qrel);

        Iterator it = qids.iterator();

        int count = 0;
        while (it.hasNext()) {
            String st = (String) it.next();
            if (!qrel.contains(st)) {
                count++;
                System.out.println(st);
            }
            

        }
        System.out.println(count);
    }

    public static void main(String[] args) throws IOException {
        ComputeStatistics cmp = new ComputeStatistics();
        //cmp.computeStatistics();
        //cmp.seperateLabels();
        //cmp.computeqIDS();
        //  cmp.prepareModifiedMSMArco();
        cmp.getUniqueIds();
    }
}
