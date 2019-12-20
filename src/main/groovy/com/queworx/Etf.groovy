package com.queworx

import groovy.transform.ToString

@ToString(includeFields=true, includeNames=true)
class Etf implements Serializable
{
	String name
	int direction
	boolean isEtn
	Integer inverseInstrumentId
}
