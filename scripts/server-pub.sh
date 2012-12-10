#!/bin/bash
source var.sh

JETTY_DIR=jetty-distribution-$JETTY_VERSION

#build package first
cd ..
mvn package
cd scripts

#copy
for i in "${SERVERS[@]}"
do
	scp -i $KEY_PAIRS ../target/top-push-1.0-SNAPSHOT.war $i:~/$JETTY_DIR/webapps/top-push-1.0-SNAPSHOT.war
	scp -i $KEY_PAIRS top-push.xml $i:~/$JETTY_DIR/contexts/top-push.xml
	scp -i $KEY_PAIRS jetty.xml $i:~/$JETTY_DIR/etc/jetty.xml
done
#run
#ssh -i $KEY_PAIRS $SERVER "killall -9 java;cd ~/$JETTY_DIR;java -Xmx4096m -jar start.jar"