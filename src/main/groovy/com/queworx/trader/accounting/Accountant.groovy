package com.queworx.trader.accounting

class Accountant
{
	private BigDecimal __budget

	public Accountant(BigDecimal budget)
	{
		__budget = budget
	}

	public void credit(BigDecimal amount)
	{
		__budget += amount
	}

	public void debit(BigDecimal amount)
	{
		__budget -= amount
	}

	public BigDecimal getBalance()
	{
		return __budget
	}
}
