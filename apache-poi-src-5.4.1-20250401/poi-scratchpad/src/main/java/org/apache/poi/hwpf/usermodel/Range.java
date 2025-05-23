/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.poi.hwpf.usermodel;

import static java.util.stream.Collectors.toList;
import static org.apache.logging.log4j.util.Unbox.box;

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.HWPFDocumentCore;
import org.apache.poi.hwpf.model.CHPX;
import org.apache.poi.hwpf.model.FileInformationBlock;
import org.apache.poi.hwpf.model.PAPX;
import org.apache.poi.hwpf.model.PropertyNode;
import org.apache.poi.hwpf.model.SEPX;
import org.apache.poi.hwpf.model.StyleSheet;
import org.apache.poi.hwpf.model.SubdocumentType;
import org.apache.poi.hwpf.sprm.CharacterSprmCompressor;
import org.apache.poi.hwpf.sprm.ParagraphSprmCompressor;
import org.apache.poi.hwpf.sprm.SprmBuffer;
import org.apache.poi.logging.PoiLogManager;
import org.apache.poi.util.DocumentFormatException;
import org.apache.poi.util.Internal;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.util.LittleEndianConsts;

/**
 * This class is the central class of the HWPF object model. All properties that
 * apply to a range of characters in a Word document extend this class.
 *
 * It is possible to insert text and/or properties at the beginning or end of a
 * range.
 *
 * Ranges are only valid if there hasn't been an insert in a prior Range since
 * the Range's creation. Once an element (text, paragraph, etc.) has been
 * inserted into a Range, subsequent Ranges become unstable.
 */
public class Range {

    private static final Logger LOG = PoiLogManager.getLogger(Range.class);

    /**
     * @deprecated POI 3.8 beta 5
     */
    @Deprecated
    public static final int TYPE_PARAGRAPH = 0;

    /**
     * @deprecated POI 3.8 beta 5
     */
    @Deprecated
    public static final int TYPE_CHARACTER = 1;

    /**
     * @deprecated POI 3.8 beta 5
     */
    @Deprecated
    public static final int TYPE_SECTION = 2;

    /**
     * @deprecated POI 3.8 beta 5
     */
    @Deprecated
    public static final int TYPE_TEXT = 3;

    /**
     * @deprecated POI 3.8 beta 5
     */
    @Deprecated
    public static final int TYPE_LISTENTRY = 4;

    /**
     * @deprecated POI 3.8 beta 5
     */
    @Deprecated
    public static final int TYPE_TABLE = 5;

    /**
     * @deprecated POI 3.8 beta 5
     */
    @Deprecated
    public static final int TYPE_UNDEFINED = 6;

    /** Needed so inserts and deletes will ripple up through containing Ranges */
    private final Range _parent;

    /** The starting character offset of this range. */
    protected final int _start;

    /** The ending character offset of this range. */
    protected int _end;

    /** The document this range belongs to. */
    protected final HWPFDocumentCore _doc;

    /** Have we loaded the section indexes yet */
    boolean _sectionRangeFound;

    /** All sections that belong to the document this Range belongs to. */
    protected final List<SEPX> _sections;

    /** The start index in the sections list for this Range */
    protected int _sectionStart;

    /** The end index in the sections list for this Range. */
    protected int _sectionEnd;

    /** Have we loaded the paragraph indexes yet. */
    protected boolean _parRangeFound;

    /** All paragraphs that belong to the document this Range belongs to. */
    protected final List<PAPX> _paragraphs;

    /** The start index in the paragraphs list for this Range, inclusive */
    protected int _parStart;

    /** The end index in the paragraphs list for this Range, exclusive */
    protected int _parEnd;

    /** Have we loaded the characterRun indexes yet. */
    protected boolean _charRangeFound;

    /** All CharacterRuns that belong to the document this Range belongs to. */
    protected List<CHPX> _characters;

    /** The start index in the characterRuns list for this Range */
    protected int _charStart;

    /** The end index in the characterRuns list for this Range. */
    protected int _charEnd;

    protected StringBuilder _text;

    /**
     * Used to construct a Range from a document. This is generally used to
     * create a Range that spans the whole document, or at least one whole part
     * of the document (eg main text, header, comment)
     *
     * @param start Starting character offset of the range.
     * @param end Ending character offset of the range.
     * @param doc The HWPFDocument the range is based on.
     */
    public Range(int start, int end, HWPFDocumentCore doc) {
        _start = start;
        _end = end;
        _doc = doc;
        _sections = _doc.getSectionTable().getSections();
        _paragraphs = _doc.getParagraphTable().getParagraphs();
        _characters = _doc.getCharacterTable().getTextRuns();
        _text = _doc.getText();
        _parent = null;

        sanityCheckStartEnd();
    }

    /**
     * Used to create Ranges that are children of other Ranges.
     *
     * @param start Starting character offset of the range.
     * @param end Ending character offset of the range.
     * @param parent The parent this range belongs to.
     */
    protected Range(int start, int end, Range parent) {
        _start = start;
        _end = end;
        _doc = parent._doc;
        _sections = parent._sections;
        _paragraphs = parent._paragraphs;
        _characters = parent._characters;
        _text = parent._text;
        _parent = parent;

        sanityCheckStartEnd();
        sanityCheck();
    }

    protected Range(Range other) {
        _parent = other._parent;
        _start = other._start;
        _end = other._end;
        _doc = other._doc;
        _sectionRangeFound = other._sectionRangeFound;
        _sections = (other._sections == null) ? null : other._sections.stream().map(SEPX::copy).collect(toList());
        _sectionStart = other._sectionStart;
        _sectionEnd = other._sectionEnd;
        _parRangeFound = other._parRangeFound;
        _paragraphs = (other._paragraphs == null) ? null : other._paragraphs.stream().map(PAPX::copy).collect(toList());
        _parStart = other._parStart;
        _parEnd = other._parEnd;
        _charRangeFound = other._charRangeFound;
        _characters = (other._characters == null) ? null : other._characters.stream().map(CHPX::copy).collect(toList());
        _charStart = other._charStart;
        _charEnd = other._charEnd;
        _text = (other._text == null) ? null : new StringBuilder(other._text);
    }


    /**
     * Ensures that the start and end were were given are actually valid, to
     * avoid issues later on if they're not
     */
    private void sanityCheckStartEnd() {
        if (_start < 0) {
            throw new IllegalArgumentException("Range start must not be negative. Given " + _start);
        }
        if (_end < _start) {
            throw new IllegalArgumentException("The end (" + _end
                    + ") must not be before the start (" + _start + ")");
        }
    }

    /**
     * Gets the text that this Range contains.
     *
     * @return The text for this range.
     */
    public String text() {
        return _text.substring( _start, _end );
    }

    /**
     * Removes any fields (eg macros, page markers etc) from the string.
     * Normally used to make some text suitable for showing to humans, and the
     * resultant text should not normally be saved back into the document!
     */
    public static String stripFields(String text) {
        // First up, fields can be nested...
        // A field can be 0x13 [contents] 0x15
        // Or it can be 0x13 [contents] 0x14 [real text] 0x15

        // If there are no fields, all easy
        if (text.indexOf('\u0013') == -1)
            return text;

        // Loop over until they're all gone
        // That's when we're out of both 0x13s and 0x15s
        while (text.indexOf('\u0013') > -1 && text.indexOf('\u0015') > -1) {
            int first13 = text.indexOf('\u0013');
            int next13 = text.indexOf('\u0013', first13 + 1);
            int first14 = text.indexOf('\u0014', first13 + 1);
            int last15 = text.lastIndexOf('\u0015');

            // If they're the wrong way around, give up
            if (last15 < first13) {
                break;
            }

            // If no more 13s and 14s, just zap
            if (next13 == -1 && first14 == -1) {
                text = text.substring(0, first13) + text.substring(last15 + 1);
                break;
            }

            // If a 14 comes before the next 13, then
            // zap from the 13 to the 14, and remove
            // the 15
            if (first14 != -1 && (first14 < next13 || next13 == -1)) {
                text = text.substring(0, first13) + text.substring(first14 + 1, last15)
                        + text.substring(last15 + 1);
                continue;
            }

            // Another 13 comes before the next 14.
            // This means there's nested stuff, so we
            // can just zap the lot
            text = text.substring(0, first13) + text.substring(last15 + 1);
        }

        return text;
    }

    /**
     * Used to get the number of sections in a range. If this range is smaller
     * than a section, it will return 1 for its containing section.
     *
     * @return The number of sections in this range.
     */
    public int numSections() {
        initSections();
        return _sectionEnd - _sectionStart;
    }

    /**
     * Used to get the number of paragraphs in a range. If this range is smaller
     * than a paragraph, it will return 1 for its containing paragraph.
     *
     * @return The number of paragraphs in this range.
     */

    public int numParagraphs() {
        initParagraphs();
        return _parEnd - _parStart;
    }

    /**
     *
     * @return The number of characterRuns in this range.
     */

    public int numCharacterRuns() {
        initCharacterRuns();
        return _charEnd - _charStart;
    }

    /**
     * Inserts text into the front of this range.
     *
     * @param text
     *            The text to insert
     * @return The character run that text was inserted into.
     */
    public CharacterRun insertBefore( String text )
    {
        initAll();

        _text.insert( _start, text );
        _doc.getCharacterTable().adjustForInsert( _charStart, text.length() );
        _doc.getParagraphTable().adjustForInsert( _parStart, text.length() );
        _doc.getSectionTable().adjustForInsert( _sectionStart, text.length() );
        if ( _doc instanceof HWPFDocument )
        {
            ( (BookmarksImpl) ( (HWPFDocument) _doc ).getBookmarks() )
                    .afterInsert( _start, text.length() );
        }
        adjustForInsert( text.length() );

        // update the FIB.CCPText + friends fields
        adjustFIB( text.length() );

        sanityCheck();

        return getCharacterRun( 0 );
    }

    /**
     * Inserts text onto the end of this range
     *
     * @param text
     *            The text to insert
     * @return The character run the text was inserted into.
     */
    public CharacterRun insertAfter( String text )
    {
        initAll();

        _text.insert( _end, text );

        _doc.getCharacterTable().adjustForInsert( _charEnd - 1, text.length() );
        _doc.getParagraphTable().adjustForInsert( _parEnd - 1, text.length() );
        _doc.getSectionTable().adjustForInsert( _sectionEnd - 1, text.length() );
        if ( _doc instanceof HWPFDocument )
        {
            ( (BookmarksImpl) ( (HWPFDocument) _doc ).getBookmarks() )
                    .afterInsert( _end, text.length() );
        }
        adjustForInsert( text.length() );

        sanityCheck();
        return getCharacterRun( numCharacterRuns() - 1 );
    }

    /**
     * Inserts text into the front of this range and it gives that text the
     * CharacterProperties specified in props.
     *
     * @param text
     *            The text to insert.
     * @param props
     *            The CharacterProperties to give the text.
     * @return A new CharacterRun that has the given text and properties and is
     *         n ow a part of the document.
     * @deprecated POI 3.8 beta 4. User code should not work with {@link CharacterProperties}
     */
    @Deprecated
    private CharacterRun insertBefore(String text, CharacterProperties props)
    {
        initAll();
        PAPX papx = _paragraphs.get(_parStart);
        short istd = papx.getIstd();

        StyleSheet ss = _doc.getStyleSheet();
        CharacterProperties baseStyle = ss.getCharacterStyle(istd);
        byte[] grpprl = CharacterSprmCompressor.compressCharacterProperty(props, baseStyle);
        SprmBuffer buf = new SprmBuffer(grpprl, 0);
        _doc.getCharacterTable().insert(_charStart, _start, buf);

        return insertBefore(text);
    }

    /**
     * Inserts text onto the end of this range and gives that text the
     * CharacterProperties specified in props.
     *
     * @param text
     *            The text to insert.
     * @param props
     *            The CharacterProperties to give the text.
     * @return A new CharacterRun that has the given text and properties and is
     *         n ow a part of the document.
     * @deprecated POI 3.8 beta 4. User code should not work with {@link CharacterProperties}
     */
    @Deprecated
    private CharacterRun insertAfter(String text, CharacterProperties props)
    {
        initAll();
        PAPX papx = _paragraphs.get(_parEnd - 1);
        short istd = papx.getIstd();

        StyleSheet ss = _doc.getStyleSheet();
        CharacterProperties baseStyle = ss.getCharacterStyle(istd);
        byte[] grpprl = CharacterSprmCompressor.compressCharacterProperty(props, baseStyle);
        SprmBuffer buf = new SprmBuffer(grpprl, 0);
        _doc.getCharacterTable().insert(_charEnd, _end, buf);
        _charEnd++;
        return insertAfter(text);
    }

    /**
     * Inserts and empty paragraph into the front of this range.
     *
     * @param props
     *            The properties that the new paragraph will have.
     * @param styleIndex
     *            The index into the stylesheet for the new paragraph.
     * @return The newly inserted paragraph.
     * @deprecated POI 3.8 beta 4. Use code shall not work with {@link ParagraphProperties}
     */
    @Deprecated
    private Paragraph insertBefore(ParagraphProperties props, int styleIndex)
    {
        return this.insertBefore(props, styleIndex, "\r");
    }

    /**
     * Inserts a paragraph into the front of this range. The paragraph will
     * contain one character run that has the default properties for the
     * paragraph's style.
     *
     * It is necessary for the text to end with the character '\r'
     *
     * @param props
     *            The paragraph's properties.
     * @param styleIndex
     *            The index of the paragraph's style in the style sheet.
     * @param text
     *            The text to insert.
     * @return A newly inserted paragraph.
     * @deprecated POI 3.8 beta 4. Use code shall not work with {@link ParagraphProperties}
     */
    @Deprecated
    private Paragraph insertBefore(ParagraphProperties props, int styleIndex, String text)
    {
        initAll();
        StyleSheet ss = _doc.getStyleSheet();
        ParagraphProperties baseStyle = ss.getParagraphStyle(styleIndex);
        CharacterProperties baseChp = ss.getCharacterStyle(styleIndex);

        byte[] grpprl = ParagraphSprmCompressor.compressParagraphProperty(props, baseStyle);
        byte[] withIndex = new byte[grpprl.length + LittleEndianConsts.SHORT_SIZE];
        LittleEndian.putShort(withIndex, 0, (short) styleIndex);
        System.arraycopy(grpprl, 0, withIndex, LittleEndianConsts.SHORT_SIZE, grpprl.length);
        SprmBuffer buf = new SprmBuffer(withIndex, 2);

        _doc.getParagraphTable().insert(_parStart, _start, buf);
        insertBefore(text, baseChp);
        return getParagraph(0);
    }

    /**
     * Inserts and empty paragraph into the end of this range.
     *
     * @param props
     *            The properties that the new paragraph will have.
     * @param styleIndex
     *            The index into the stylesheet for the new paragraph.
     * @return The newly inserted paragraph.
     * @deprecated POI 3.8 beta 4. Use code shall not work with {@link ParagraphProperties}
     */
    @Deprecated
    Paragraph insertAfter(ParagraphProperties props, int styleIndex)
    {
        return this.insertAfter(props, styleIndex, "\r");
    }

    /**
     * Inserts a paragraph into the end of this range. The paragraph will
     * contain one character run that has the default properties for the
     * paragraph's style.
     *
     * It is necessary for the text to end with the character '\r'
     *
     * @param props
     *            The paragraph's properties.
     * @param styleIndex
     *            The index of the paragraph's style in the style sheet.
     * @param text
     *            The text to insert.
     * @return A newly inserted paragraph.
     * @deprecated POI 3.8 beta 4. Use code shall not work with {@link ParagraphProperties}
     */
    @Deprecated
    Paragraph insertAfter(ParagraphProperties props, int styleIndex, String text)
    {
        initAll();
        StyleSheet ss = _doc.getStyleSheet();
        ParagraphProperties baseStyle = ss.getParagraphStyle(styleIndex);
        CharacterProperties baseChp = ss.getCharacterStyle(styleIndex);

        byte[] grpprl = ParagraphSprmCompressor.compressParagraphProperty(props, baseStyle);
        byte[] withIndex = new byte[grpprl.length + LittleEndianConsts.SHORT_SIZE];
        LittleEndian.putShort(withIndex, 0, (short) styleIndex);
        System.arraycopy(grpprl, 0, withIndex, LittleEndianConsts.SHORT_SIZE, grpprl.length);
        SprmBuffer buf = new SprmBuffer(withIndex, 2);

        _doc.getParagraphTable().insert(_parEnd, _end, buf);
        _parEnd++;
        insertAfter(text, baseChp);
        return getParagraph(numParagraphs() - 1);
    }

    public void delete() {

        initAll();

        int numSections = _sections.size();
        int numRuns = _characters.size();
        int numParagraphs = _paragraphs.size();

        for (int x = _charStart; x < numRuns; x++) {
            CHPX chpx = _characters.get(x);
            chpx.adjustForDelete(_start, _end - _start);
        }

        for (int x = _parStart; x < numParagraphs; x++) {
            PAPX papx = _paragraphs.get(x);
            // System.err.println("Paragraph " + x + " was " + papx.getStart() +
            // " -> " + papx.getEnd());
            papx.adjustForDelete(_start, _end - _start);
            // System.err.println("Paragraph " + x + " is now " +
            // papx.getStart() + " -> " + papx.getEnd());
        }

        for (int x = _sectionStart; x < numSections; x++) {
            SEPX sepx = _sections.get(x);
            // System.err.println("Section " + x + " was " + sepx.getStart() +
            // " -> " + sepx.getEnd());
            sepx.adjustForDelete(_start, _end - _start);
            // System.err.println("Section " + x + " is now " + sepx.getStart()
            // + " -> " + sepx.getEnd());
        }

        if ( _doc instanceof HWPFDocument )
        {
            ( (BookmarksImpl) ( (HWPFDocument) _doc ).getBookmarks() )
                    .afterDelete( _start, ( _end - _start ) );
        }

        _text.delete( _start, _end );
        Range parent = _parent;
        if ( parent != null )
        {
            parent.adjustForInsert( -( _end - _start ) );
        }

        // update the FIB.CCPText + friends field
        adjustFIB(-(_end - _start));
    }

    /**
     * Inserts a simple table into the beginning of this range.
     *
     * @param columns
     *            The number of columns
     * @param rows
     *            The number of rows.
     * @return The empty Table that is now part of the document.
     */
    public Table insertTableBefore(short columns, int rows) {
        ParagraphProperties parProps = new ParagraphProperties();
        parProps.setFInTable(true);
        parProps.setItap( 1 );

        final int oldEnd = this._end;

        for ( int x = 0; x < rows; x++ )
        {
            Paragraph cell = this.insertBefore( parProps, StyleSheet.NIL_STYLE );
            cell.insertAfter( String.valueOf( '\u0007' ) );
            for ( int y = 1; y < columns; y++ )
            {
                cell = cell.insertAfter( parProps, StyleSheet.NIL_STYLE );
                cell.insertAfter( String.valueOf( '\u0007' ) );
            }
            cell = cell.insertAfter( parProps, StyleSheet.NIL_STYLE,
                    String.valueOf( '\u0007' ) );
            cell.setTableRowEnd( new TableProperties( columns ) );
        }

        final int newEnd = this._end;
        final int diff = newEnd - oldEnd;

        return new Table( _start, _start + diff, this, 1 );
    }

    /**
     * Replace range text with new one, adding it to the range and deleting
     * original text from document
     *
     * @param newText
     *            The text to be replaced with
     * @param addAfter
     *            if {@code true} the text will be added at the end of current
     *            range, otherwise to the beginning
     */
    public void replaceText( String newText, boolean addAfter )
    {
        if ( addAfter )
        {
            int originalEnd = getEndOffset();
            insertAfter( newText );
            new Range( getStartOffset(), originalEnd, this ).delete();
        }
        else
        {
            int originalStart = getStartOffset();
            int originalEnd = getEndOffset();

            insertBefore( newText );
            new Range( originalStart + newText.length(), originalEnd
                    + newText.length(), this ).delete();
        }
    }

    /**
     * Replace (one instance of) a piece of text with another...
     *
     * @param pPlaceHolder
     *            The text to be replaced (e.g., "${organization}")
     * @param pValue
     *            The replacement text (e.g., "Apache Software Foundation")
     * @param pOffset
     *            The offset or index where the text to be replaced begins
     *            (relative to/within this {@code Range})
     */
    @Internal
    public void replaceText(String pPlaceHolder, String pValue, int pOffset) {
        int absPlaceHolderIndex = getStartOffset() + pOffset;

        Range subRange = new Range(absPlaceHolderIndex,
                (absPlaceHolderIndex + pPlaceHolder.length()), this);
        subRange.insertBefore(pValue);

        // re-create the sub-range so we can delete it
        subRange = new Range((absPlaceHolderIndex + pValue.length()), (absPlaceHolderIndex
                + pPlaceHolder.length() + pValue.length()), this);

        // deletes are automagically propagated
        subRange.delete();
    }

    /**
     * Replace (all instances of) a piece of text with another...
     *
     * @param pPlaceHolder
     *            The text to be replaced (e.g., "${organization}")
     * @param pValue
     *            The replacement text (e.g., "Apache Software Foundation")
     */
    public void replaceText(String pPlaceHolder, String pValue) {
        while (true) {
            String text = text();
            int offset = text.indexOf(pPlaceHolder);
            if (offset >= 0) {
                replaceText(pPlaceHolder, pValue, offset);
            } else {
                break;
            }
        }
    }

    /**
     * Gets the character run at index. The index is relative to this range.
     *
     * @param index
     *            The index of the character run to get.
     * @return The character run at the specified index in this range.
     */
    public CharacterRun getCharacterRun( int index )
    {
        initCharacterRuns();

        if ( index + _charStart >= _charEnd )
            throw new IndexOutOfBoundsException( "CHPX #" + index + " ("
                    + ( index + _charStart ) + ") not in range [" + _charStart
                    + "; " + _charEnd + ")" );

        CHPX chpx = _characters.get( index + _charStart );
        if ( chpx == null )
        {
            return null;
        }

        short istd;
        if ( this instanceof Paragraph )
        {
            istd = ((Paragraph) this)._istd;
        }
        else
        {
            int[] point = findRange( _paragraphs,
                    Math.max( chpx.getStart(), _start ),
                    Math.min( chpx.getEnd(), _end ) );

            initParagraphs();
            int parStart = Math.max( point[0], _parStart );

            if ( parStart >= _paragraphs.size() )
            {
                return null;
            }

            PAPX papx = _paragraphs.get( point[0] );
            istd = papx.getIstd();
        }

        return new CharacterRun( chpx, _doc.getStyleSheet(), istd,
                this);
    }

    /**
     * Gets the section at index. The index is relative to this range.
     *
     * @param index
     *            The index of the section to get.
     * @return The section at the specified index in this range.
     */
    public Section getSection(int index) {
        initSections();
        SEPX sepx = _sections.get(index + _sectionStart);
        return new Section(sepx, this);
    }

    /**
     * Gets the paragraph at index. The index is relative to this range.
     *
     * @param index
     *            The index of the paragraph to get.
     * @return The paragraph at the specified index in this range.
     */

    public Paragraph getParagraph(int index) {
        initParagraphs();

        if ( index + _parStart >= _parEnd )
            throw new IndexOutOfBoundsException( "Paragraph #" + index + " ("
                    + (index + _parStart) + ") not in range [" + _parStart
                    + "; " + _parEnd + ")" );

        PAPX papx = _paragraphs.get(index + _parStart);
        return Paragraph.newParagraph( this, papx );
    }

    /**
     * Gets the table that starts with paragraph. In a Word file, a table
     * consists of a group of paragraphs with certain flags set.
     *
     * @param paragraph
     *            The paragraph that is the first paragraph in the table.
     * @return The table that starts with paragraph
     */
    public Table getTable(Paragraph paragraph) {
        if (!paragraph.isInTable()) {
            throw new IllegalArgumentException("This paragraph doesn't belong to a table");
        }

        Range r = paragraph;
        if (r._parent != this) {
            throw new IllegalArgumentException("This paragraph is not a child of this range instance");
        }

        r.initAll();
        int tableLevel = paragraph.getTableLevel();
        int tableEndInclusive = r._parStart;

        if ( r._parStart != 0 )
        {
            Paragraph previous = Paragraph.newParagraph( this,
                    _paragraphs.get( r._parStart - 1 ) );
            if ( previous.isInTable() && //
                    previous.getTableLevel() == tableLevel //
                    && previous._sectionEnd >= r._sectionStart )
            {
                throw new IllegalArgumentException(
                        "This paragraph is not the first one in the table" );
            }
        }

        Range overallRange = _doc.getOverallRange();
        int limit = _paragraphs.size();
        for ( ; tableEndInclusive < limit - 1; tableEndInclusive++ )
        {
            Paragraph next = Paragraph.newParagraph( overallRange,
                    _paragraphs.get( tableEndInclusive + 1 ) );
            if ( !next.isInTable() || next.getTableLevel() < tableLevel )
                break;
        }

        initAll();
        if ( tableEndInclusive >= this._parEnd )
        {
            LOG.atWarn().log("The table's bounds [{}; {}) fall outside of this Range paragraphs numbers [{}; {})",
                    this._parStart, box(tableEndInclusive),box(this._parStart),box(this._parEnd));
        }

        if ( tableEndInclusive < 0 )
        {
            throw new ArrayIndexOutOfBoundsException(
                    "The table's end is negative, which isn't allowed!" );
        }

        int endOffsetExclusive = _paragraphs.get( tableEndInclusive ).getEnd();

        return new Table( paragraph.getStartOffset(), endOffsetExclusive,
                this, paragraph.getTableLevel() );
    }

    /**
     * loads all of the list indexes.
     */
    protected void initAll() {
        initCharacterRuns();
        initParagraphs();
        initSections();
    }

    /**
     * inits the paragraph list indexes.
     */
    private void initParagraphs() {
        if (!_parRangeFound) {
            int[] point = findRange(_paragraphs, _start, _end);
            _parStart = point[0];
            _parEnd = point[1];
            _parRangeFound = true;
        }
    }

    /**
     * inits the character run list indexes.
     */
    private void initCharacterRuns() {
        if (!_charRangeFound) {
            int[] point = findRange(_characters, _start, _end);
            _charStart = point[0];
            _charEnd = point[1];
            _charRangeFound = true;
        }
    }

    /**
     * inits the section list indexes.
     */
    private void initSections() {
        if (!_sectionRangeFound) {
            int[] point = findRange(_sections, _sectionStart, _start, _end);
            _sectionStart = point[0];
            _sectionEnd = point[1];
            _sectionRangeFound = true;
        }
    }

    private static int binarySearchStart( List<? extends PropertyNode<?>> rpl,
            int start )
    {
        if ( rpl.isEmpty())
            return -1;
        if ( rpl.get( 0 ).getStart() >= start )
            return 0;

        int low = 0;
        int high = rpl.size() - 1;

        while ( low <= high )
        {
            int mid = ( low + high ) >>> 1;
            PropertyNode<?> node = rpl.get( mid );

            if ( node.getStart() < start )
            {
                low = mid + 1;
            }
            else if ( node.getStart() > start )
            {
                high = mid - 1;
            }
            else
            {
                assert node.getStart() == start;
                return mid;
            }
        }
        assert low != 0;
        return low - 1;
    }

    private static int binarySearchEnd( List<? extends PropertyNode<?>> rpl,
            int foundStart, int end )
    {
        if ( rpl.get( rpl.size() - 1 ).getEnd() <= end )
            return rpl.size() - 1;

        int low = foundStart;
        int high = rpl.size() - 1;

        while ( low <= high )
        {
            int mid = ( low + high ) >>> 1;
            PropertyNode<?> node = rpl.get( mid );

            if ( node.getEnd() < end )
            {
                low = mid + 1;
            }
            else if ( node.getEnd() > end )
            {
                high = mid - 1;
            }
            else
            {
                assert node.getEnd() == end;
                return mid;
            }
        }
        assert 0 <= low && low < rpl.size();

        return low;
    }

    /**
     * Used to find the list indexes of a particular property.
     *
     * @param rpl
     *            A list of property nodes.
     * @param start
     *            The starting character offset.
     * @param end
     *            The ending character offset.
     * @return An int array of length 2. The first int is the start index and
     *         the second int is the end index.
     */
    private int[] findRange( List<? extends PropertyNode<?>> rpl, int start,
            int end )
    {
        int startIndex = binarySearchStart( rpl, start );
        while ( startIndex > 0 && rpl.get( startIndex - 1 ).getStart() >= start )
            startIndex--;

        int endIndex = binarySearchEnd( rpl, startIndex, end );
        while ( endIndex < rpl.size() - 1
                && rpl.get( endIndex + 1 ).getEnd() <= end )
            endIndex++;

        if ( startIndex < 0 || startIndex >= rpl.size()
                || startIndex > endIndex || endIndex < 0
                || endIndex >= rpl.size() ) {
            throw new DocumentFormatException("problem finding range");
        }

        return new int[] { startIndex, endIndex + 1 };
    }

    /**
     * Used to find the list indexes of a particular property.
     *
     * @param rpl
     *            A list of property nodes.
     * @param min
     *            A hint on where to start looking.
     * @param start
     *            The starting character offset.
     * @param end
     *            The ending character offset.
     * @return An int array of length 2. The first int is the start index and
     *         the second int is the end index.
     */
    private int[] findRange(List<? extends PropertyNode<?>> rpl, int min, int start, int end) {
        int x = min;

        if ( rpl.size() == min )
            return new int[] { min, min };

        PropertyNode<?> node = rpl.get( x );

        while (node==null || (node.getEnd() <= start && x < rpl.size() - 1)) {
            x++;

            if (x>=rpl.size()) {
                return new int[] {0, 0};
            }

            node = rpl.get(x);
        }

        if ( node.getStart() > end )
        {
            return new int[] { 0, 0 };
        }

        if ( node.getEnd() <= start )
        {
            return new int[] { rpl.size(), rpl.size() };
        }

        for ( int y = x; y < rpl.size(); y++ )
        {
            node = rpl.get( y );
            if ( node == null )
                continue;

            if ( node.getStart() < end && node.getEnd() <= end )
                continue;

            if ( node.getStart() < end )
                return new int[] { x, y +1 };

            return new int[] { x, y };
        }
        return new int[] { x, rpl.size() };
    }

    /**
     * resets the list indexes.
     */
    protected void reset() {
        _charRangeFound = false;
        _parRangeFound = false;
        _sectionRangeFound = false;
    }

    /**
     * Adjust the value of the various FIB character count fields, eg
     * {@code FIB.CCPText} after an insert or a delete...
     *
     * Works on all CCP fields from this range onwards
     *
     * @param adjustment
     *            The (signed) value that should be added to the FIB CCP fields
     */
    protected void adjustFIB( int adjustment )
    {
        if (!( _doc instanceof HWPFDocument)) {
            throw new IllegalArgumentException("doc must be instance of HWPFDocument");
        }

        // update the FIB.CCPText field (this should happen once per adjustment,
        // so we don't want it in
        // adjustForInsert() or it would get updated multiple times if the range
        // has a parent)
        // without this, OpenOffice.org (v. 2.2.x) does not see all the text in
        // the document

        FileInformationBlock fib = _doc.getFileInformationBlock();

        // // Do for each affected part
        // if (_start < cpS.getMainDocumentEnd()) {
        // fib.setCcpText(fib.getCcpText() + adjustment);
        // }
        //
        // if (_start < cpS.getCommentsEnd()) {
        // fib.setCcpAtn(fib.getCcpAtn() + adjustment);
        // }
        // if (_start < cpS.getEndNoteEnd()) {
        // fib.setCcpEdn(fib.getCcpEdn() + adjustment);
        // }
        // if (_start < cpS.getFootnoteEnd()) {
        // fib.setCcpFtn(fib.getCcpFtn() + adjustment);
        // }
        // if (_start < cpS.getHeaderStoryEnd()) {
        // fib.setCcpHdd(fib.getCcpHdd() + adjustment);
        // }
        // if (_start < cpS.getHeaderTextboxEnd()) {
        // fib.setCcpHdrTxtBx(fib.getCcpHdrTxtBx() + adjustment);
        // }
        // if (_start < cpS.getMainTextboxEnd()) {
        // fib.setCcpTxtBx(fib.getCcpTxtBx() + adjustment);
        // }

        // much simple implementation base on SubdocumentType --sergey

        int currentEnd = 0;
        for ( SubdocumentType type : SubdocumentType.ORDERED )
        {
            int currentLength = fib.getSubdocumentTextStreamLength( type );
            currentEnd += currentLength;

            // do we need to shift this part?
            if ( _start > currentEnd )
                continue;

            fib.setSubdocumentTextStreamLength( type, currentLength
                    + adjustment );

            break;
        }
    }

    /**
     * adjust this range after an insert happens.
     *
     * @param length
     *            the length to adjust for (expected to be a count of
     *            code-points, not necessarily chars)
     */
    private void adjustForInsert(int length) {
        _end += length;

        reset();
        Range parent = _parent;
        if (parent != null) {
            parent.adjustForInsert(length);
        }
    }

    /**
     * @return Starting character offset of the range
     */
    public int getStartOffset() {
        return _start;
    }

    /**
     * @return The ending character offset of this range
     */
    public int getEndOffset() {
        return _end;
    }

    protected HWPFDocumentCore getDocument() {
        return _doc;
    }

    @Override
    public String toString()
    {
        return "Range from " + getStartOffset() + " to " + getEndOffset()
                + " (chars)";
    }

    /**
     * Method for debug purposes. Checks that all resolved elements are inside
     * of current range.  Throws {@link IllegalArgumentException} if checks fail.
     */
    public boolean sanityCheck()
    {
        DocumentFormatException.check(_start >= 0,
                "start can't be < 0");
        DocumentFormatException.check( _start <= _text.length(),
                "start can't be > text length");
        DocumentFormatException.check( _end >= 0,
                "end can't be < 0");
        DocumentFormatException.check( _end <= _text.length(),
                "end can't be > text length");
        DocumentFormatException.check( _start <= _end,"start can't be > end");

        if ( _charRangeFound )
        {
            for ( int c = _charStart; c < _charEnd; c++ )
            {
                CHPX chpx = _characters.get( c );

                int left = Math.max( this._start, chpx.getStart() );
                int right = Math.min( this._end, chpx.getEnd() );
                DocumentFormatException.check(left < right, "left must be < right");
            }
        }
        if ( _parRangeFound )
        {
            for ( int p = _parStart; p < _parEnd; p++ )
            {
                PAPX papx = _paragraphs.get( p );

                int left = Math.max( this._start, papx.getStart() );
                int right = Math.min( this._end, papx.getEnd() );

                DocumentFormatException.check( left < right,
                        "left must be < right");
            }
        }
        return true;
    }
}
