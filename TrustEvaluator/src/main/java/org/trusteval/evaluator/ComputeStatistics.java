/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.trusteval.evaluator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 *
 * @author Procheta
 */
public class ComputeStatistics {
   public void computeStatistics() throws FileNotFoundException, IOException{
       FileReader fr = new FileReader(new File("C://Users//Procheta//Downloads/robust-uqv.txt"));
       BufferedReader br = new BufferedReader(fr);
       
       String line = br.readLine();
       
       double avgLength = 0;
       int lineCount = 0;
       while(line != null){
           String st[] = line.split(";");
           String tokens[] = st[1].split("\\s+");
           avgLength += tokens.length;
           line = br.readLine();
           lineCount++;
       }
       avgLength /=lineCount;
       System.out.println("Avg number of words " + avgLength);
   }
   
   public static void main(String[] args) throws IOException{
       ComputeStatistics cmp = new  ComputeStatistics();
       cmp.computeStatistics();
   }
}
