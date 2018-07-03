package pl.bubson.notepadjw.core;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.databases.BiblesDatabase;
import pl.bubson.notepadjw.utils.Language;

import static pl.bubson.notepadjw.core.BookNamesMapping.filePrefixMap;

/**
 * Created by Kuba on 2016-02-15.
 */
public class Verse {

    private static final String TAG = "Verse";
    private static final String CHAPTER_PATTERN = "(\\d{1,3})";
    private static final String VERSE_PATTERN = "(\\d{1,3}(\\s*[,\\p{Pd}]\\s*\\d{1,3})*)"; // \\p{Pd} contains all Dash Punctuation, like - or –
    private static final String CHAPTER_AND_VERSE_PATTERN = CHAPTER_PATTERN + "[:,]\\s*" + VERSE_PATTERN;
    private static Language language;
    private static BookNamesMapping mapping;
    private static String MULTIPLE_CHAPTER_LAST_VERSE_PATTERN;
    private static String SINGLE_CHAPTER_LAST_VERSE_PATTERN;
    private static String UNIVERSAL_VERSE_PATTERN;
    private Context context;
    private String bookName;
    private String unifiedBookName;
    private List<VerseNumbers> verseNumbersList;
    private String text = "";

    public Verse(Context context, String verseDescriptorToParse, Language language) {
        Log.v(TAG, "Verse instance creation started");
        this.context = context;
        setVerseLanguageIfNeeded(language);
        parseDescriptor(verseDescriptorToParse);
        setText();
        Log.v(TAG, "Verse instance created");
    }

    public static boolean isTextContainingVerseAtTheEnd(String text, Language language) {
        setVerseLanguageIfNeeded(language);
        Pattern pattern = Pattern.compile(MULTIPLE_CHAPTER_LAST_VERSE_PATTERN);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find() && (mapping.getMultipleChapterBookMap().containsKey(mapping.unifyBookName(matcher.group(1))))) {
            return true;
        }
        Pattern pattern2 = Pattern.compile(SINGLE_CHAPTER_LAST_VERSE_PATTERN);
        Matcher matcher2 = pattern2.matcher(text);
        if (matcher2.find() && (mapping.getSingleChapterBookMap().containsKey(mapping.unifyBookName(matcher2.group(1))))) {
            return true;
        }
        return false;
    }

    public static String getTextOfLastVerse(Context context, String textWithVerse, Language language) {
        setVerseLanguageIfNeeded(language);
        Pattern pattern = Pattern.compile(MULTIPLE_CHAPTER_LAST_VERSE_PATTERN);
        Matcher matcher = pattern.matcher(textWithVerse);
        if (matcher.find() && (mapping.getMultipleChapterBookMap().containsKey(mapping.unifyBookName(matcher.group(1))))) {
            Verse verse = new Verse(context, matcher.group(0), language);
            return verse.getVerseDescriptorAndTextInHtmlForm();
        }
        Pattern pattern2 = Pattern.compile(SINGLE_CHAPTER_LAST_VERSE_PATTERN);
        Matcher matcher2 = pattern2.matcher(textWithVerse);
        if (matcher2.find() && (mapping.getSingleChapterBookMap().containsKey(mapping.unifyBookName(matcher2.group(1))))) {
            Verse verse = new Verse(context, matcher2.group(0), language);
            return verse.getVerseDescriptorAndTextInHtmlForm();
        }
        return "";
    }

    public static String getUniversalVersePattern(Language language) {
        setVerseLanguageIfNeeded(language);
        return UNIVERSAL_VERSE_PATTERN;
    }

    private static void setVerseLanguageIfNeeded(Language lang) {
        if ((language == null) || (!language.equals(lang))) {
            Log.v(TAG, "setVerseLanguageIfNeeded started");

            language = lang;
            mapping = BookNamesMapping.getInstance(lang);

            String BOOK_PATTERN = "((" + mapping.getMultiplePartNameBookWithoutLastWordPattern() + "|[1-5][^\\S\\n]*)?[\\p{Lu}\\p{Ll}]+\\.?)";
            if (language.equals(Language.de)) BOOK_PATTERN = getGermanBookPattern();
            String MULTIPLE_CHAPTER_VERSE_PATTERN = BOOK_PATTERN + "((\\s*" + CHAPTER_AND_VERSE_PATTERN + "\\s?;\\s?)+|(\\s)+)" + CHAPTER_AND_VERSE_PATTERN;
            String SINGLE_CHAPTER_VERSE_PATTERN = "(" + mapping.getSingleChapterBooksPattern() + ")" + "\\s+" + VERSE_PATTERN;
            MULTIPLE_CHAPTER_LAST_VERSE_PATTERN = MULTIPLE_CHAPTER_VERSE_PATTERN + "\\s*$";
            SINGLE_CHAPTER_LAST_VERSE_PATTERN = SINGLE_CHAPTER_VERSE_PATTERN + "\\s*$";
            UNIVERSAL_VERSE_PATTERN = MULTIPLE_CHAPTER_VERSE_PATTERN + "|" + SINGLE_CHAPTER_VERSE_PATTERN;
//            UNIVERSAL_VERSE_PATTERN = "((" + BOOK_PATTERN + "\\s+" + CHAPTER_PATTERN + "[:,]\\s*)" + "|" +
//                    "(" + mapping.getSingleChapterBooksPattern() + ")\\s+)" + VERSE_PATTERN; // previous version, equal effect, probably similar performance
            Log.v(TAG, "setVerseLanguageIfNeeded finished");
        } else {
            Log.v(TAG, "setVerseLanguageIfNeeded - not needed");
        }
    }

    @NonNull
    private static String getGermanBookPattern() {
        // Germans use dot after numbers, e.g. "1. Kor. 1:2". But below pattern cannot be used elsewhere, because it produced ambiguity,
        // e.g. "1. Kor. 1:1. Jana 1:2" will be showing "1. Jana 1:2" instead of "Jana 1:2"
        return "((" + mapping.getMultiplePartNameBookWithoutLastWordPattern() + "|[1-5]\\.?[^\\S\\n]*)?[\\p{Lu}\\p{Ll}]+\\.?)";
    }

    private void parseDescriptor(String verseDescriptorToParse) {
        Log.v(TAG, "parseDescriptor started");
        verseNumbersList = new ArrayList<>();
        Pattern pattern = Pattern.compile(MULTIPLE_CHAPTER_LAST_VERSE_PATTERN);
        Matcher matcher = pattern.matcher(verseDescriptorToParse);
        if (matcher.find() && (mapping.getMultipleChapterBookMap().containsKey(mapping.unifyBookName(matcher.group(1))))) {
            bookName = matcher.group(1);
            unifiedBookName = mapping.unifyBookName(bookName);
            if (!matcher.group(3).trim().equals("")) {
                String[] additionalVerses = matcher.group(3).trim().split(";");
                for (String additionalVerse : additionalVerses) {
                    addVerseNumbers(additionalVerse.trim());
                }
            }
            verseNumbersList.add(new VerseNumbers(Integer.parseInt(matcher.group(9)), matcher.group(10)));
        } else {
            Pattern pattern2 = Pattern.compile(SINGLE_CHAPTER_LAST_VERSE_PATTERN);
            Matcher matcher2 = pattern2.matcher(verseDescriptorToParse);
            if (matcher2.find() && (mapping.getSingleChapterBookMap().containsKey(mapping.unifyBookName(matcher2.group(1))))) {
                bookName = matcher2.group(1);
                unifiedBookName = mapping.unifyBookName(bookName);
                verseNumbersList.add(new VerseNumbers(0, matcher2.group(2)));
            }
        }
        Log.v(TAG, "parseDescriptor finished");
    }

    private void addVerseNumbers(String chapterAndVersesToParse) {
        Pattern pattern = Pattern.compile(CHAPTER_AND_VERSE_PATTERN);
        Matcher matcher = pattern.matcher(chapterAndVersesToParse);
        if (matcher.find()) verseNumbersList.add(new VerseNumbers(Integer.parseInt(matcher.group(1)), matcher.group(2)));
    }

    public String getText() {
        return text;
    }

    public String getVerseDescriptorAndTextInHtmlForm() {
        if (verseNumbersList.get(0).chapter != 0) {
            StringBuilder result = new StringBuilder("<b>" + bookName + " ");
            String delimiter = "";
            for (VerseNumbers verse : verseNumbersList) {
                result.append(delimiter).append(verse.chapter).append(":").append(verse.verses);
                delimiter = "; ";
            }
            result.append("</b><br>").append(text);
            return result.toString();
        } else {
            return "<b>" + bookName + " " + verseNumbersList.get(0).verses + "</b><br>" + text;
        }
    }

    private void setText() {
        if (mapping.getAllBooksMap().containsKey(unifiedBookName)) {
            for (VerseNumbers verse : verseNumbersList) {
                int htmlChapterNumber = verse.chapter == 0 ? 1 : verse.chapter;

                try {
                    Log.v(TAG, "BiblesDatabase.getFile() - started");
                    BiblesDatabase dbh = new BiblesDatabase(context);
                    String xhtml = dbh.getFile(language, getResourceName(verse.chapter));
                    Log.v(TAG, "BiblesDatabase.getFile() - finished: " + getResourceName(verse.chapter));

                    if (xhtml != null) {
                        StringBuilder stringBuilder = new StringBuilder();
                        for (String verseGroup : verse.verses.split("\\s?,\\s?")) {
                            String[] splittedGroup = verseGroup.split("\\s?\\p{Pd}\\s?"); // \\p{Pd} contains all Dash Punctuation, like - or –
                            int startVerse, endVerse;
                            startVerse = Integer.parseInt(splittedGroup[0].trim());
                            if (splittedGroup.length == 1) {
                                endVerse = Integer.parseInt(splittedGroup[0].trim());
                            } else {
                                endVerse = Integer.parseInt(splittedGroup[1].trim());
                            }

                            if (startVerse <= endVerse) {
                                String currentId = "chapter" + htmlChapterNumber + "_verse" + startVerse;
                                String nextId = "chapter" + htmlChapterNumber + "_verse" + (endVerse + 1);
                                String startTag = "<(span|a) id=\"" + currentId + "\"></(span|a)>";
                                String endTagOrEndOfChapter = "((<(span|a) id=\"" + nextId + "\"></(span|a)>)|(</body>)|<div)";
                                Pattern pattern = Pattern.compile("(?s)" + startTag + "(.+?)" + endTagOrEndOfChapter);
                                Matcher matcher = pattern.matcher(xhtml);
                                Log.v(TAG, "setText - start searching verse in string");
                                if (matcher.find() && (matcher.group(3) != null)) {
//                            String receivedVerses = matcher.group(1); // other option - plain html without parsing
                                    String receivedVerses = Jsoup.parse(matcher.group(3)).text().replaceFirst("\\d{1,3}", String.valueOf(startVerse));
                                    stringBuilder.append(receivedVerses).append(" ");
                                } else {
                                    stringBuilder.append(verseGroup).append(" ").append(context.getResources().getString(R.string.wrong_verse)).append(" ");
                                }
                                Log.v(TAG, "setText - finished searching verse in string");
                            } else {
                                stringBuilder.append(verseGroup).append(" ").append(context.getResources().getString(R.string.wrong_verse_order)).append(" ");
                            }
                        }
                        text = text + stringBuilder.toString();
                        Log.v(TAG, "setText - whole method finished");
                    } else {
                        text = text + context.getResources().getString(R.string.wrong_chapter);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    text = context.getResources().getString(R.string.unexpected_exception);
                }
            }
        } else {
            text = context.getResources().getString(R.string.wrong_book);
        }

    }

    private String getResourceName(int chapterNumber) {
        Map<String, String> map = mapping.getAllBooksMap();
        String bookDescriptor = map.get(unifiedBookName);
        int htmlChapterNumber = chapterNumber == 0 ? 1 : chapterNumber;
        String chapterDescriptor = htmlChapterNumber == 1 ? "" : "-split" + String.valueOf(htmlChapterNumber);
        return filePrefixMap.get(language) + bookDescriptor + chapterDescriptor + ".xhtml";
    }
}
