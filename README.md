# trader
An automated algorithmic trader written in Groovy

Has the ability to watch over a large section of the market and trade custom strategies. Can trade through InteractiveBrokers and MB Trading. Can receive feeds from Yahoo and ActiveTick.

Use Maven to compile and run it. To run just type

mvn exec:java -Dexec.mainClass="com.queworx.trader.Runner"