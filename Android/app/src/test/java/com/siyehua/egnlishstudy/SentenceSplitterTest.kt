package com.siyehua.egnlishstudy

import com.siyehua.egnlishstudy.data.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitterTest {
    @Test
    fun split_keepsDottedAbbreviationsInsideSentence() {
        val text = "He moved to L.A. after college. He later worked in the U.S. market."

        assertEquals(
            listOf(
                "He moved to L.A. after college.",
                "He later worked in the U.S. market."
            ),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_allowsSentenceToEndAfterAbbreviation() {
        val text = "He worked in the U.S. Then he moved to Canada."

        assertEquals(
            listOf(
                "He worked in the U.S.",
                "Then he moved to Canada."
            ),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_keepsNormalSentenceBoundaries() {
        val text = "What happened? Nothing changed! It is fine."

        assertEquals(
            listOf("What happened?", "Nothing changed!", "It is fine."),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_keepsNumberAbbreviationInsideSentence() {
        val text = "Choose No. 5 from the list. Then read it aloud."

        assertEquals(
            listOf(
                "Choose No. 5 from the list.",
                "Then read it aloud."
            ),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_allowsNoAsACompleteSentence() {
        val text = "No. I do not agree."

        assertEquals(
            listOf("No.", "I do not agree."),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_keepsTitleAbbreviationInsideSentence() {
        val text = "Dr. Smith lives in the U.S. market area. He teaches English."

        assertEquals(
            listOf(
                "Dr. Smith lives in the U.S. market area.",
                "He teaches English."
            ),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_keepsCommonAbbreviationsInsideSentence() {
        val text = "Prof. Lee joined Acme Inc. in Jan. 2026. Dr. Green stayed at St. Mary's."

        assertEquals(
            listOf(
                "Prof. Lee joined Acme Inc. in Jan. 2026.",
                "Dr. Green stayed at St. Mary's."
            ),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_keepsLatinAbbreviationsInsideSentence() {
        val text = "Use short examples, e.g. simple news sentences. Avoid rare words, i.e. words learners never see."

        assertEquals(
            listOf(
                "Use short examples, e.g. simple news sentences.",
                "Avoid rare words, i.e. words learners never see."
            ),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_keepsDecimalAndDomainInsideSentence() {
        val text = "The rate rose to 3.5 percent. Visit www.example.com for details."

        assertEquals(
            listOf(
                "The rate rose to 3.5 percent.",
                "Visit www.example.com for details."
            ),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_keepsSectionAbbreviationInsideSentence() {
        val text = "Read Ch. 2 before class. See Fig. 3 for the chart."

        assertEquals(
            listOf(
                "Read Ch. 2 before class.",
                "See Fig. 3 for the chart."
            ),
            SentenceSplitter.split(text)
        )
    }

    @Test
    fun split_keepsSpacedInitialAbbreviationInsideSentence() {
        val text = "The U. S. market changed quickly. The U. K. report followed."

        assertEquals(
            listOf(
                "The U. S. market changed quickly.",
                "The U. K. report followed."
            ),
            SentenceSplitter.split(text)
        )
    }
}
