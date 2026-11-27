package com.ramwiper.next.domain
import org.junit.Assert.*
import org.junit.Test
class HistoryCsvTest {
    @Test fun quotesNewlinesAndNeutralizesSpreadsheetFormula() {
        val r = CleanupResult(0,"NATIVE","SOFT",100,80,16,0,0,false,"=SUM(1,2)\"\ntext",
            Outcome.COMPLETED,70)
        val csv = HistoryCsv.encode(listOf(r))
        assertTrue(csv.contains("1970-01-01T00:00:00Z"))
        assertTrue(csv.contains("\"-30\""))
        assertTrue(csv.contains("\"'=SUM(1,2)\"\"\ntext\""))
        assertTrue(csv.endsWith("\r\n"))
    }
    @Test fun emptyHistoryExportsHeaderOnly() {
        assertEquals(1, HistoryCsv.encode(emptyList()).trimEnd().lines().size)
    }
}
