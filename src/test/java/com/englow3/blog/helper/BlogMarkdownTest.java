package com.englow3.blog.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BlogMarkdownTest {

    private static final UUID FIRST = UUID.fromString("6f1c2a40-1b2c-4d3e-8f90-a1b2c3d4e5f6");
    private static final UUID SECOND = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Nested
    class ImageReferences {

        @Test
        void idsComeBackInTheOrderTheyFirstAppear() {
            String body = "![a](blog-image:%s) text ![b](blog-image:%s)".formatted(SECOND, FIRST);

            assertThat(BlogMarkdown.imageReferences(body)).containsExactly(SECOND, FIRST);
        }

        @Test
        void anImageNamedTwiceCountsOnce() {
            String body = "![a](blog-image:%s) and again ![b](blog-image:%s) and ![c](blog-image:%s)".formatted(FIRST,
                    SECOND, FIRST);

            assertThat(BlogMarkdown.imageReferences(body)).containsExactly(FIRST, SECOND);
        }

        @Test
        void theSameIdInUpperCaseIsTheSameImage() {
            String body = "blog-image:%s blog-image:%s".formatted(FIRST, FIRST.toString().toUpperCase());

            assertThat(BlogMarkdown.imageReferences(body)).containsExactly(FIRST);
        }

        @Test
        void aMalformedIdIsIgnoredAndTheGoodOnesAroundItAreKept() {
            String body = ("blog-image:oops ![x](blog-image:not-a-uuid-at-all) blog-image:%s "
                    + "blog-image:zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz blog-image:1234 blog-image:%s").formatted(FIRST,
                            SECOND);

            assertThat(BlogMarkdown.imageReferences(body)).containsExactly(FIRST, SECOND);
        }

        @Test
        void aPostWithoutImagesOrWithoutTextHasNoReferences() {
            assertThat(BlogMarkdown.imageReferences("just words, and a ![remote](https://example.com/a.png)"))
                    .isEmpty();
            assertThat(BlogMarkdown.imageReferences("")).isEmpty();
            assertThat(BlogMarkdown.imageReferences(null)).isEmpty();
        }

        @Test
        void aReferenceThatIsNotFollowedByAnIdIsNotOne() {
            assertThat(BlogMarkdown.imageReferences("blog-image: and blog-image:")).isEqualTo(List.of());
        }
    }

    @Nested
    class Excerpt {

        @Test
        void shortPlainTextIsKeptAsItIs() {
            assertThat(BlogMarkdown.excerpt("A short note about the past perfect."))
                    .isEqualTo("A short note about the past perfect.");
        }

        @Test
        void nothingGivesNothing() {
            assertThat(BlogMarkdown.excerpt(null)).isEmpty();
            assertThat(BlogMarkdown.excerpt("")).isEmpty();
            assertThat(BlogMarkdown.excerpt("   \n  ")).isEmpty();
        }

        @Test
        void imageSyntaxIsRemovedEntirely() {
            String body = "Before ![a diagram](blog-image:%s) after".formatted(FIRST);

            assertThat(BlogMarkdown.excerpt(body)).isEqualTo("Before after");
        }

        @Test
        void aLinkBecomesItsText() {
            assertThat(BlogMarkdown.excerpt("Read [the Cambridge guide](https://example.com/guide?a=1) first"))
                    .isEqualTo("Read the Cambridge guide first");
        }

        @Test
        void headingsEmphasisCodeAndQuotesLoseTheirSymbols() {
            String body = "# Title\n\n## Part two\n**bold** and *italic* and __strong__ and _em_ and `code`\n> quoted";

            assertThat(BlogMarkdown.excerpt(body))
                    .isEqualTo("Title Part two bold and italic and strong and em and code quoted");
        }

        @Test
        void aSymbolInsideAWordIsPartOfTheWord() {
            assertThat(BlogMarkdown.excerpt("Use snake_case names, C# and a > b"))
                    .isEqualTo("Use snake_case names, C# and a > b");
        }

        @Test
        void whitespaceIsCollapsed() {
            assertThat(BlogMarkdown.excerpt("  one\n\n\ttwo   three \n")).isEqualTo("one two three");
        }

        @Test
        void textOfExactly200CharactersIsNotCut() {
            String body = "x".repeat(200);

            assertThat(BlogMarkdown.excerpt(body)).isEqualTo(body);
        }

        @Test
        void longerTextIsCutAtAWordBoundaryWithAnEllipsisWithin200Characters() {
            String body = "word ".repeat(100);

            String excerpt = BlogMarkdown.excerpt(body);

            assertThat(excerpt).endsWith("word…");
            assertThat(excerpt.length()).isLessThanOrEqualTo(200);
            assertThat(excerpt).doesNotContain("  ").doesNotContain(" …");
        }

        @Test
        void theCutNeverLeavesPartOfAWord() {
            String body = "alpha beta gamma delta ".repeat(30);

            String excerpt = BlogMarkdown.excerpt(body);

            String withoutEllipsis = excerpt.substring(0, excerpt.length() - 1);
            assertThat(List.of("alpha", "beta", "gamma", "delta"))
                    .contains(withoutEllipsis.substring(withoutEllipsis.lastIndexOf(' ') + 1));
        }

        @Test
        void aSingleWordLongerThanTheExcerptIsCutHard() {
            String excerpt = BlogMarkdown.excerpt("x".repeat(500));

            assertThat(excerpt).hasSize(200).endsWith("…");
        }

        @Test
        void aHardCutDoesNotSplitACharacterMadeOfTwoUnits() {
            // 198 units of "x" then an emoji (two units): the hard cut at 199 falls between the two halves of the
            // emoji.
            String excerpt = BlogMarkdown.excerpt("x".repeat(198) + "😀😀");

            assertThat(excerpt).isEqualTo("x".repeat(198) + "…");
        }

        /**
         * The list reads the first 1200 characters of a post, which can stop anywhere - inside an image, whose
         * reference must never reach the excerpt.
         */
        @Test
        void textThatEndsAtEveryPointOfAnImageNeverShowsItsReference() {
            String full = "Intro text. ![a diagram](blog-image:%s) outro".formatted(FIRST);

            for (int end = 0; end <= full.length(); end++) {
                String head = full.substring(0, end);

                assertThat(BlogMarkdown.excerpt(head)).as("ends at %d: '%s'", end, head).doesNotContain("blog-image")
                        .doesNotContain(FIRST.toString().substring(0, 8)).doesNotContain("![").doesNotContain("](");
            }
        }

        @Test
        void textCutInsideAnImageKeepsOnlyTheWordsBeforeIt() {
            String head = "Intro text. ![a diagram](blog-image:6f1c2a40-1b2c";

            assertThat(BlogMarkdown.excerpt(head)).isEqualTo("Intro text.");
            assertThat(BlogMarkdown.excerpt("Intro text. ![a dia")).isEqualTo("Intro text.");
            assertThat(BlogMarkdown.excerpt("Intro text. ![a diagram](")).isEqualTo("Intro text.");
        }

        @Test
        void textThatEndsInsideALinkKeepsTheLinkTextAndNotItsAddress() {
            assertThat(BlogMarkdown.excerpt("See [the guide](https://exam")).isEqualTo("See the guide");
            assertThat(BlogMarkdown.excerpt("See [the gui")).isEqualTo("See the gui");
        }

        @Test
        void aReferenceTypedAsPlainTextIsNotShownEither() {
            assertThat(BlogMarkdown.excerpt("look at blog-image:%s here".formatted(FIRST))).isEqualTo("look at here");
        }

        /** {@code b} is a hexadecimal digit: the fragment of one reference must not eat the start of the next. */
        @Test
        void aReferenceRightAfterAnotherLeavesNothingOfEither() {
            assertThat(BlogMarkdown.excerpt("blog-image:blog-image:")).isEmpty();
            assertThat(BlogMarkdown.excerpt("a blog-image:blog-image:%s z".formatted(FIRST))).isEqualTo("a z");
            assertThat(BlogMarkdown.excerpt("a blog-image:6f1c-blog-image:blog-image: z")).isEqualTo("a z");
            assertThat(BlogMarkdown.excerpt("blog-image:abcblog-image:abc")).isEmpty();
        }

        /**
         * From the second {@code blog-image:} on, wherever the text stops, none of the id and neither keyword with its
         * colon is left. (A cut inside the word of the second reference leaves letters of a word, not of an id.)
         */
        @Test
        void textThatEndsAnywhereInTheIdOfTwoReferencesInARowNeverShowsEither() {
            String text = "Intro. blog-image:blog-image:%s".formatted(FIRST);
            int firstId = text.indexOf("blog-image:blog-image:") + "blog-image:blog-image:".length();

            for (int end = firstId; end <= text.length(); end++) {
                String head = text.substring(0, end);

                assertThat(BlogMarkdown.excerpt(head)).as("ends at %d: '%s'", end, head).isEqualTo("Intro.");
            }
        }

        @Test
        void textAfterAReferenceThatIsNotMadeOfIdCharactersIsKept() {
            assertThat(BlogMarkdown.excerpt("blog-image:%s so on".formatted(FIRST))).isEqualTo("so on");
            assertThat(BlogMarkdown.excerpt("blog-image: and then")).isEqualTo("and then");
            assertThat(BlogMarkdown.excerpt("blog-image:%s, then".formatted(FIRST))).isEqualTo(", then");
        }

        /**
         * A run of opening brackets was tried from each one to the end of the line: 20 000 of them took about two
         * seconds. The excerpt of a post is made from at most 1200 characters, but a comment or a future caller is not
         * bound by that, and a request must not be able to burn a thread by what it types.
         */
        @Test
        void aLongRunOfOpeningBracketsIsLinear() {
            String body = "[".repeat(20_000);

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> BlogMarkdown.excerpt(body));
            assertThat(BlogMarkdown.excerpt("[".repeat(20_000))).hasSizeLessThanOrEqualTo(200);
        }

        @Test
        void openingBracketsFollowedByClosingOnesAreLinearToo() {
            String body = "[".repeat(10_000) + "]".repeat(10_000);

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> BlogMarkdown.excerpt(body));
        }

        @Test
        void aLongRunOfOpeningBracketsBeforeALineBreakIsLinear() {
            String body = "[".repeat(20_000) + "\nend";

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> BlogMarkdown.excerpt(body));
        }

        @Test
        void aLinkAfterAStrayBracketKeepsItsText() {
            assertThat(BlogMarkdown.excerpt("see [a [real link](https://example.com) now"))
                    .isEqualTo("see a real link now");
        }
    }
}
