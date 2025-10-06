exec: target/ulc-0.1.0-SNAPSHOT.jar
	java -cp $(<) io.github.zhuguiyuan.Entry

target/ulc-0.1.0-SNAPSHOT.jar: clean
	mvn package

clean:
	mvn clean

.PHONY: exec clean
