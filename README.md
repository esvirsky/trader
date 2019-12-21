# trader
An automated algorithmic trader written in Groovy

Has the ability to watch over a large section of the market and trade custom strategies. Can trade through InteractiveBrokers and MB Trading. Can receive feeds from Yahoo and ActiveTick.

Use Maven to compile and run it, you'll need to install some jars into your local repo:

mvn install:install-file -Dfile=lib\tws-api.jar -DgroupId=tws-api -DartifactId=tws-api -Dversion=1.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib\activetick\atapi.jar -DgroupId=activetick -DartifactId=activetick -Dversion=1.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib\activetick\gnu-crypto.jar -DgroupId=activetick -DartifactId=gun-crypto -Dversion=1.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib\activetick\javax-crypto.jar -DgroupId=activetick -DartifactId=javax-crypto -Dversion=1.0 -Dpackaging=jar
mvn install:install-file -Dfile=lib\activetick\javax-security.jar -DgroupId=activetick -DartifactId=javax-security -Dversion=1.0 -Dpackaging=jar

mvn compile
mvn exec:java

Contact me at eddie@queworx.com if you need some help with any of this - http://www.queworx.com