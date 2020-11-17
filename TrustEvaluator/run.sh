#!/bin/bash
mvn compile


INDEX=C:/Users/Procheta/Downloads/index_lucene_5.3/
QUERY=C:/Users/Procheta/Downloads/robust-uqv.txt
FEEDBACK=true
QRELS=C:/Users/Procheta/Downloads/TrustEvaluator/data/qrels/qrels.trec6.adhoc
RESFILE=C:/Users/Procheta/Desktop/result.txt

for k in 2 5 8 10 
do
for b in 0.1 0.2 0.3 0.4 0.5 0.6 0.7 

do
cat > retrieve.properties << EOF1
index=$INDEX

#result file path
res.file=$RESFILE

#query file path
query.file=$QUERY


#feedback used or not
feedback=$FEEDBACK

#evaluation flag
eval=true

#number of topdocs used for feedback
fdbk.numtopdocs=$k

#stopwordFile
stopfile=stop.txt

qrels.file=$QRELS

#number of topdocs retrieved
retrieve.num_wanted=10
#evaluate.graded=true

#query expansion using rlm flag
rlm.qe=true

#query expansion term weight using rlm
rlm.qe.newterms.wt=$b

#number of expansion terms
rlm.qe.nterms=10

#collection Trec/MSMARCO
collection=Trec

#evaluation mode
evalMode=trust1

#rlm type (uni, bi, iid, conditional)
rlm.type=bi

#kde sigma value
gaussian.sigma=10

#kde h value
kde.h=1

#kde kernel type
kde.kernel=gaussian

#wordvec file
wordvecs.vecfile=C:/Users/Procheta/Downloads/tmp.vec

#wordvec file type
wordvecs.readfrom=vec

#retrieval model (BM25,LM)
retrieveModel=BM25

#querypairs file path
querypairs.file=C:/Users/Procheta/Downloads/robust-uqv.txt

fieldName=words
k=0.8
b=0.7


EOF1

mvn exec:java@retrieve -Dexec

done
done
