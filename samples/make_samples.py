#!/usr/bin/env python3
"""
Writes the sample documents of samples/, to try DocComparator on (README, "Samples"):

  procedure-revA.docx      the reference: a maintenance procedure, revision A (Safety on a new page, a page break
                           before Records)
  procedure-revB.docx      its next version: paragraphs added and removed, sentences changed, two sections swapped,
                           its figure redrawn, a table row added, formatting changed, a paragraph indented, the
                           table's header shaded and taller, a cell's border, extra blanks, tracked changes, another
                           footer and template
  training-handbook.docx   another document, on another template, with other headings and styles, that copied
                           paragraphs from revision A, in another order, and changed pieces of them

Written as Word writes them (the parts, their relationships, the styles, the numbering, a header, a footer,
footnotes), with Python's standard library alone. The output is the same at each run: run it again after a change.

    python3 samples/make_samples.py
"""

import os
import struct
import zipfile
import zlib
from xml.sax.saxutils import escape

HERE = os.path.dirname(os.path.abspath(__file__))

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
NS = f'xmlns:w="{W}" xmlns:r="{R}"'
DECLARATION = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'

# Each zip entry gets this date, so that a run writes the same bytes as the one before.
ZIP_DATE = (2026, 11, 4, 0, 0, 0)


# ---------------------------------------------------------------------------------------------------------------------
# Runs and paragraphs

def run(text, b=False, i=False, u=False, style=None):
    """A run of text; b, i, u: bold, italic, underlined."""
    props = ""
    if style:
        props += f'<w:rStyle w:val="{style}"/>'
    if b:
        props += "<w:b/>"
    if i:
        props += "<w:i/>"
    if u:
        props += '<w:u w:val="single"/>'
    rpr = f"<w:rPr>{props}</w:rPr>" if props else ""
    return f'<w:r>{rpr}<w:t xml:space="preserve">{escape(text)}</w:t></w:r>'


_revision = [100]


def _change_id():
    _revision[0] += 1
    return _revision[0]


def inserted(text, author="M. Laurent"):
    """Text inserted with track changes on."""
    return (f'<w:ins w:id="{_change_id()}" w:author="{author}" w:date="2026-10-20T09:30:00Z">'
            f'{run(text)}</w:ins>')


def deleted(text, author="M. Laurent"):
    """Text deleted with track changes on: still in the file, no longer in the text."""
    return (f'<w:del w:id="{_change_id()}" w:author="{author}" w:date="2026-10-20T09:31:00Z">'
            f'<w:r><w:delText xml:space="preserve">{escape(text)}</w:delText></w:r></w:del>')


def footnote_reference(note_id, style):
    return f'<w:r><w:rPr><w:rStyle w:val="{style}"/></w:rPr><w:footnoteReference w:id="{note_id}"/></w:r>'


def paragraph(*content, style=None, num=None, level=0, extra=""):
    """A paragraph of runs (a plain string is a run of its own) in a style, an item of the list num if given."""
    ppr = ""
    if style:
        ppr += f'<w:pStyle w:val="{style}"/>'
    if num is not None:
        ppr += f'<w:numPr><w:ilvl w:val="{level}"/><w:numId w:val="{num}"/></w:numPr>'
    ppr += extra
    body = "".join(part if part.startswith("<") else run(part) for part in content)
    return f"<w:p>{'<w:pPr>' + ppr + '</w:pPr>' if ppr else ''}{body}</w:p>"


def table(rows, style, header=True, header_fill=None, header_height=None, borders=None):
    """
    A table of text cells; the first row repeated on each page as its header, shaded with header_fill if given (a
    colour, "D9E2F3"), header_height high (twentieths of a point). borders: {(row, column): its border's width, in
    eighths of a point}. Each cell carries Word's w:cnfStyle, its row's banding as the table style sees it: Word
    rewrites it below a row inserted, which is no difference anyone made.
    """
    columns = len(rows[0])
    width = 9000 // columns
    grid = "".join(f'<w:gridCol w:w="{width}"/>' for _ in range(columns))
    xml = (f'<w:tbl><w:tblPr><w:tblStyle w:val="{style}"/><w:tblW w:w="0" w:type="auto"/>'
           f'<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" w:lastColumn="0" '
           f'w:noHBand="0" w:noVBand="1"/></w:tblPr><w:tblGrid>{grid}</w:tblGrid>')
    borders = borders or {}
    for index, cells in enumerate(rows):
        trpr = ""
        if header and index == 0:
            height = f'<w:trHeight w:val="{header_height}"/>' if header_height else ""
            trpr = f"<w:trPr>{height}<w:tblHeader/></w:trPr>"
        xml += f"<w:tr>{trpr}"
        band = "100000000000" if index == 0 else ("000000100000" if index % 2 else "000000010000")
        for column, cell in enumerate(cells):
            content = run(cell, b=header and index == 0)
            tcpr = f'<w:cnfStyle w:val="{band}"/><w:tcW w:w="{width}" w:type="dxa"/>'
            if (index, column) in borders:
                tcpr += (f'<w:tcBorders><w:bottom w:val="single" w:sz="{borders[(index, column)]}" w:space="0" '
                         f'w:color="C00000"/></w:tcBorders>')
            if header and index == 0 and header_fill:
                tcpr += f'<w:shd w:val="clear" w:color="auto" w:fill="{header_fill}"/>'
            xml += f'<w:tc><w:tcPr>{tcpr}</w:tcPr><w:p>{content}</w:p></w:tc>'
        xml += "</w:tr>"
    return xml + "</w:tbl>"


def section_properties():
    return ('<w:sectPr><w:headerReference w:type="default" r:id="rIdHeader"/>'
            '<w:footerReference w:type="default" r:id="rIdFooter"/>'
            '<w:pgSz w:w="11906" w:h="16838"/>'
            '<w:pgMar w:top="1417" w:right="1417" w:bottom="1417" w:left="1417" w:header="708" w:footer="708" '
            'w:gutter="0"/></w:sectPr>')


# ---------------------------------------------------------------------------------------------------------------------
# Styles and numbering

def styles_part(ids, font, size, heading_color, title_size):
    """The styles, under the ids given by role; their names are Word's, in English whatever the language."""
    def style(kind, role, name, ppr="", rpr="", based_on="Normal", extra=""):
        based = f'<w:basedOn w:val="{ids[based_on]}"/>' if based_on else ""
        return (f'<w:style w:type="{kind}" w:styleId="{ids[role]}"><w:name w:val="{name}"/>{based}{extra}'
                f'{"<w:pPr>" + ppr + "</w:pPr>" if ppr else ""}{"<w:rPr>" + rpr + "</w:rPr>" if rpr else ""}'
                f"</w:style>")

    xml = (f"<w:styles {NS}><w:docDefaults><w:rPrDefault><w:rPr>"
           f'<w:rFonts w:ascii="{font}" w:hAnsi="{font}" w:eastAsia="{font}" w:cs="{font}"/>'
           f'<w:sz w:val="{size}"/><w:szCs w:val="{size}"/><w:lang w:val="en-GB"/></w:rPr></w:rPrDefault>'
           f'<w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="259" w:lineRule="auto"/></w:pPr></w:pPrDefault>'
           f"</w:docDefaults>")
    xml += (f'<w:style w:type="paragraph" w:default="1" w:styleId="{ids["Normal"]}"><w:name w:val="Normal"/>'
            f"<w:qFormat/></w:style>")
    xml += style("paragraph", "Title", "Title", ppr='<w:spacing w:after="240"/><w:contextualSpacing/>',
                 rpr=f'<w:b/><w:color w:val="{heading_color}"/><w:sz w:val="{title_size}"/>',
                 extra=f'<w:next w:val="{ids["Normal"]}"/><w:qFormat/>')
    xml += style("paragraph", "Heading1", "heading 1",
                 ppr='<w:keepNext/><w:spacing w:before="360" w:after="120"/><w:outlineLvl w:val="0"/>',
                 rpr=f'<w:b/><w:color w:val="{heading_color}"/><w:sz w:val="32"/>',
                 extra=f'<w:next w:val="{ids["Normal"]}"/><w:qFormat/>')
    xml += style("paragraph", "Heading2", "heading 2",
                 ppr='<w:keepNext/><w:spacing w:before="240" w:after="80"/><w:outlineLvl w:val="1"/>',
                 rpr=f'<w:b/><w:color w:val="{heading_color}"/><w:sz w:val="26"/>',
                 extra=f'<w:next w:val="{ids["Normal"]}"/><w:qFormat/>')
    xml += style("paragraph", "ListParagraph", "List Paragraph", ppr='<w:ind w:left="720"/><w:contextualSpacing/>',
                 extra="<w:qFormat/>")
    xml += style("paragraph", "FootnoteText", "footnote text", ppr='<w:spacing w:after="0"/>',
                 rpr='<w:sz w:val="18"/>')
    xml += style("character", "FootnoteReference", "footnote reference", based_on=None,
                 rpr='<w:vertAlign w:val="superscript"/>')
    xml += style("paragraph", "Header", "header", ppr='<w:jc w:val="right"/>', rpr='<w:sz w:val="18"/>')
    xml += style("paragraph", "Footer", "footer", ppr='<w:jc w:val="center"/>', rpr='<w:sz w:val="18"/>')
    xml += (f'<w:style w:type="table" w:styleId="{ids["TableGrid"]}"><w:name w:val="Table Grid"/>'
            f'<w:pPr><w:spacing w:after="0"/></w:pPr><w:tblPr><w:tblBorders>'
            + "".join(f'<w:{side} w:val="single" w:sz="4" w:space="0" w:color="auto"/>'
                      for side in ("top", "left", "bottom", "right", "insideH", "insideV"))
            + '</w:tblBorders><w:tblCellMar><w:left w:w="108" w:type="dxa"/><w:right w:w="108" w:type="dxa"/>'
              "</w:tblCellMar></w:tblPr></w:style>")
    return DECLARATION + xml + "</w:styles>"


BULLETS = 1         # num 1: bullets
STEPS = 2           # num 2: 1., 2., 3.…
STEPS_AGAIN = 3     # num 3: 1., 2., 3.… from 1 again, for a second numbered list


def numbering_part():
    def abstract(abstract_id, fmt, text, font=None):
        rpr = f'<w:rPr><w:rFonts w:ascii="{font}" w:hAnsi="{font}" w:hint="default"/></w:rPr>' if font else ""
        return (f'<w:abstractNum w:abstractNumId="{abstract_id}"><w:multiLevelType w:val="hybridMultilevel"/>'
                f'<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="{fmt}"/><w:lvlText w:val="{text}"/>'
                f'<w:lvlJc w:val="left"/><w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr>{rpr}</w:lvl>'
                f"</w:abstractNum>")
    return (DECLARATION + f"<w:numbering {NS}>"
            + abstract(0, "bullet", "\u2022")
            + abstract(1, "decimal", "%1.")
            + f'<w:num w:numId="{BULLETS}"><w:abstractNumId w:val="0"/></w:num>'
            + f'<w:num w:numId="{STEPS}"><w:abstractNumId w:val="1"/></w:num>'
            + f'<w:num w:numId="{STEPS_AGAIN}"><w:abstractNumId w:val="1"/>'
              f'<w:lvlOverride w:ilvl="0"><w:startOverride w:val="1"/></w:lvlOverride></w:num>'
            + "</w:numbering>")


# ---------------------------------------------------------------------------------------------------------------------
# The package

def write_docx(name, *, title, author, body, styles, header, footer, footnotes, template_link, template_name,
               ids, images=None, header_images=None):
    """
    A .docx in samples/, its styles under these ids (by role). header and footer: lists of paragraphs' XML. footnotes: (id, text) pairs, ids from 1.
    template_link: the template the settings attach, as Word links it (a path on a share); template_name: the one
    the properties name.
    """
    def part(root, content):
        return DECLARATION + f"<w:{root} {NS}>{content}</w:{root}>"

    notes = ('<w:footnote w:type="separator" w:id="-1"><w:p><w:pPr><w:spacing w:after="0"/></w:pPr>'
             '<w:r><w:separator/></w:r></w:p></w:footnote>'
             '<w:footnote w:type="continuationSeparator" w:id="0"><w:p><w:pPr><w:spacing w:after="0"/></w:pPr>'
             '<w:r><w:continuationSeparator/></w:r></w:p></w:footnote>')
    for note_id, text in footnotes:
        notes += (f'<w:footnote w:id="{note_id}"><w:p><w:pPr><w:pStyle w:val="{ids["FootnoteText"]}"/></w:pPr>'
                  f'<w:r><w:rPr><w:rStyle w:val="{ids["FootnoteReference"]}"/></w:rPr><w:footnoteRef/></w:r>'
                  f'{run(" " + text)}</w:p></w:footnote>')

    def relationships(links):
        xml = (DECLARATION + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">')
        for rid, kind, target, external in links:
            kind = kind if "://" in kind else REL + kind
            mode = ' TargetMode="External"' if external else ""
            xml += f'<Relationship Id="{rid}" Type="{kind}" Target="{escape(target)}"{mode}/>'
        return xml + "</Relationships>"

    word = "application/vnd.openxmlformats-officedocument.wordprocessingml."
    content_types = (
        DECLARATION + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Default Extension="png" ContentType="image/png"/>'
        f'<Override PartName="/word/document.xml" ContentType="{word}document.main+xml"/>'
        f'<Override PartName="/word/styles.xml" ContentType="{word}styles+xml"/>'
        f'<Override PartName="/word/numbering.xml" ContentType="{word}numbering+xml"/>'
        f'<Override PartName="/word/settings.xml" ContentType="{word}settings+xml"/>'
        f'<Override PartName="/word/footnotes.xml" ContentType="{word}footnotes+xml"/>'
        f'<Override PartName="/word/header1.xml" ContentType="{word}header+xml"/>'
        f'<Override PartName="/word/footer1.xml" ContentType="{word}footer+xml"/>'
        '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
        '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>'
        "</Types>")

    settings = part("settings",
                    '<w:attachedTemplate r:id="rIdTemplate"/><w:trackRevisions/><w:defaultTabStop w:val="708"/>'
                    '<w:footnotePr><w:footnote w:id="-1"/><w:footnote w:id="0"/></w:footnotePr>'
                    '<w:compat><w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word" '
                    'w:val="15"/></w:compat>')

    core = (DECLARATION + '<cp:coreProperties '
            'xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
            'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" '
            'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
            f"<dc:title>{escape(title)}</dc:title><dc:creator>{escape(author)}</dc:creator>"
            f"<cp:lastModifiedBy>{escape(author)}</cp:lastModifiedBy>"
            '<dcterms:created xsi:type="dcterms:W3CDTF">2026-10-01T08:00:00Z</dcterms:created>'
            '<dcterms:modified xsi:type="dcterms:W3CDTF">2026-10-20T10:00:00Z</dcterms:modified>'
            "</cp:coreProperties>")
    app = (DECLARATION + '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">'
           f"<Template>{escape(template_name)}</Template><Application>Microsoft Office Word</Application>"
           "<AppVersion>16.0000</AppVersion></Properties>")

    entries = {
        "[Content_Types].xml": content_types,
        "_rels/.rels": relationships([
            ("rId1", "officeDocument", "word/document.xml", False),
            ("rId2", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
             "docProps/core.xml", False),
            ("rId3", "extended-properties", "docProps/app.xml", False)]),
        "word/document.xml": part("document", f"<w:body>{body}{section_properties()}</w:body>"),
        "word/_rels/document.xml.rels": relationships([
            ("rIdStyles", "styles", "styles.xml", False),
            ("rIdNumbering", "numbering", "numbering.xml", False),
            ("rIdSettings", "settings", "settings.xml", False),
            ("rIdFootnotes", "footnotes", "footnotes.xml", False),
            ("rIdHeader", "header", "header1.xml", False),
            ("rIdFooter", "footer", "footer1.xml", False)]),
        "word/styles.xml": styles,
        "word/numbering.xml": numbering_part(),
        "word/settings.xml": settings,
        "word/_rels/settings.xml.rels": relationships([("rIdTemplate", "attachedTemplate", template_link, True)]),
        "word/footnotes.xml": part("footnotes", notes),
        "word/header1.xml": part("hdr", "".join(header)),
        "word/footer1.xml": part("ftr", "".join(footer)),
        "docProps/core.xml": core,
        "docProps/app.xml": app,
    }
    # pictures: word/media/<file>, linked from the body (images) or from the header, by its own relationships
    images = images or {}
    header_images = header_images or {}
    if images:
        links = "".join(f'<Relationship Id="rId{rid}" Type="{REL}image" Target="media/{file}"/>'
                        for rid, (file, _) in images.items())
        entries["word/_rels/document.xml.rels"] = entries["word/_rels/document.xml.rels"].replace(
            "</Relationships>", links + "</Relationships>")
    if header_images:
        entries["word/_rels/header1.xml.rels"] = relationships(
            [("rId" + rid, "image", "media/" + file, False) for rid, (file, _) in header_images.items()])
    media = dict(list(images.values()) + list(header_images.values()))
    path = os.path.join(HERE, name)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as docx:
        for entry, content in entries.items():
            info = zipfile.ZipInfo(entry, ZIP_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            docx.writestr(info, content.encode("utf-8"))
        for file, data in media.items():
            info = zipfile.ZipInfo("word/media/" + file, ZIP_DATE)
            info.compress_type = zipfile.ZIP_STORED
            docx.writestr(info, data)
    print("wrote", os.path.relpath(path, os.getcwd()))


# ---------------------------------------------------------------------------------------------------------------------
# Pictures: small PNG images drawn here, and the drawing that shows one, as Word writes it

def png(width, height, colour_at):
    """A PNG image, each pixel the (r, g, b) colour_at(x, y) gives."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    rows = b"".join(b"\x00" + b"".join(bytes(colour_at(x, y)) for x in range(width)) for y in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def pump(outlet):
    """A diagram of the pump: its body, its motor on its shaft, its base, its outlet in the colour given."""
    def colour_at(x, y):
        if 40 <= x < 150 and 50 <= y < 110:
            return (60, 80, 110)                       # the body
        if (x - 190) ** 2 + (y - 80) ** 2 < 30 ** 2:
            return (120, 130, 140)                     # the motor
        if 150 <= x < 165 and 72 <= y < 88:
            return (90, 90, 90)                        # the shaft
        if 80 <= x < 110 and 20 <= y < 50:
            return outlet                              # the outlet
        if 110 <= y < 118 and 30 <= x < 230:
            return (150, 120, 80)                      # the base
        return (235, 242, 250)
    return png(240, 140, colour_at)


def logo():
    """Keldar's logo: an orange square, a white band across."""
    return png(48, 48, lambda x, y: (255, 255, 255) if abs(x - y) < 6 else (230, 120, 30))


def picture(rid, width_pt, height_pt, description, number):
    """A picture in the text (wp:inline), as Word writes one: its size in EMUs, 12700 to the point."""
    cx, cy = int(width_pt * 12700), int(height_pt * 12700)
    return ('<w:r><w:drawing>'
            '<wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" '
            'distT="0" distB="0" distL="0" distR="0">'
            f'<wp:extent cx="{cx}" cy="{cy}"/>'
            f'<wp:docPr id="{number}" name="Picture {number}" descr="{escape(description)}"/>'
            '<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
            '<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            f'<pic:nvPicPr><pic:cNvPr id="0" name="picture{number}.png"/><pic:cNvPicPr/></pic:nvPicPr>'
            f'<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
            f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm>'
            '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>'
            '</wp:inline></w:drawing></w:r>')


PUMP = pump((60, 140, 90))             # the outlet in green
PUMP_REDRAWN = pump((200, 50, 40))     # revision B's: the outlet in red
LOGO = logo()


def page_number(style):
    """A footer paragraph ending with the page number, a field as Word writes it."""
    return lambda text: paragraph(text, '<w:fldSimple w:instr=" PAGE "><w:r><w:t>1</w:t></w:r></w:fldSimple>',
                                  style=style)


# ---------------------------------------------------------------------------------------------------------------------
# The procedure, revision A and revision B

# Where Word's layout began a page when Word last saved the document (w:lastRenderedPageBreak), as Word writes it: the
# samples are not laid out by Word, so their pages are said here, as if they had been.
WORD_PAGE = '<w:r><w:lastRenderedPageBreak/></w:r>'

PROCEDURE_IDS = {role: role for role in ("Normal", "Title", "Heading1", "Heading2", "ListParagraph", "FootnoteText",
                                         "FootnoteReference", "Header", "Footer", "TableGrid")}


def procedure_revision_a():
    ids = PROCEDURE_IDS
    h1 = lambda text: paragraph(text, style=ids["Heading1"])
    bullet = lambda *text: paragraph(*text, style=ids["ListParagraph"], num=BULLETS)
    step = lambda *text: paragraph(*text, style=ids["ListParagraph"], num=STEPS)
    body = "".join([
        paragraph("Maintenance Procedure — KP-40 Hydraulic Pump", style=ids["Title"]),
        paragraph("Document KP-MP-012, revision A, approved by the workshop manager."),

        h1("Purpose"),
        paragraph("This procedure describes the periodic maintenance of the KP-40 hydraulic pump installed on the test "
                  "benches of building C."),
        paragraph("It applies to every technician who has completed the basic hydraulics training and holds a valid "
                  "workshop badge."),
        paragraph(picture("rIdPump", 180, 105, "The KP-40 pump: its body, motor and outlet", 1),
                  extra='<w:jc w:val="center"/>'),
        paragraph(run("Figure 1: the KP-40 hydraulic pump.", i=True), extra='<w:jc w:val="center"/>'),

        h1("Scope"),
        paragraph("The following operations are covered:"),
        bullet("Visual inspection of the pump housing and hoses"),
        bullet("Replacement of the suction filter"),
        bullet("Check of the oil level and colour"),
        bullet("Measurement of the outlet pressure"),
        paragraph("Repairs of the motor and of the electrical cabinet are not covered by this document; they are "
                  "described in procedure KP-MP-020."),

        paragraph(WORD_PAGE, "Safety", style=ids["Heading1"], extra="<w:pageBreakBefore/>"),               # a new page
        paragraph("Before any operation, the pump must be stopped, isolated and locked out with a personal padlock."),
        paragraph("The residual pressure in the circuit can reach 12 bar for several minutes after the stop",
                  footnote_reference(1, ids["FootnoteReference"]),
                  "; wait at least ten minutes before opening any connection."),
        paragraph("Wear safety glasses and oil-resistant gloves at all times."),

        h1("Procedure"),
        step("Stop the pump and lock out the main switch."),
        step("Place a drip tray under the filter housing."),
        step("Unscrew the filter cover by hand, without any tool."),
        step(WORD_PAGE, "Remove the old filter element and discard it with the oily waste."),   # Word's page 3
        step("Fit a new element, reference KP-F-110, and screw the cover back."),
        step("Restart the pump and check that no leak appears around the cover."),
        paragraph("The parts needed for one maintenance are listed below."),
        table([["Part", "Reference", "Quantity"],
               ["Suction filter element", "KP-F-110", "1"],
               ["Cover seal", "KP-J-031", "1"],
               ["Hydraulic oil ISO VG 46", "KP-H-046", "2 litres"]], ids["TableGrid"]),

        paragraph('<w:r><w:br w:type="page"/></w:r>'),                                          # a page break, alone
        paragraph(WORD_PAGE, "Records", style=ids["Heading1"]),   # Word's record of the page the break began
        paragraph("Record the date, the outlet pressure and your name in the maintenance log kept next to the bench. "
                  "A pressure below 180 bar ", run("must", b=True),
                  " be reported to the workshop manager the same day."),
    ])
    write_docx("procedure-revA.docx", title="Maintenance Procedure KP-MP-012", author="A. Moreau", body=body,
               styles=styles_part(ids, "Calibri", 22, "1F3864", 48),
               header=[paragraph(picture("rIdLogo", 18, 18, "Keldar's logo", 9),
                                " Keldar Pumps — Maintenance Procedure KP-MP-012", style=ids["Header"])],
               footer=[page_number(ids["Footer"])("Revision A — Page ")],
               footnotes=[(1, "Measured on the bench of building C in March 2025.")],
               template_link="file:///\\\\fileserver\\templates\\Keldar-Procedure.dotx",
               template_name="Keldar-Procedure.dotx", ids=ids,
               images={"Pump": ("pump.png", PUMP)}, header_images={"Logo": ("logo.png", LOGO)})


def procedure_revision_b():
    """Revision A, edited: each change is said where it is made."""
    ids = PROCEDURE_IDS
    h1 = lambda text: paragraph(text, style=ids["Heading1"])
    h2 = lambda text: paragraph(text, style=ids["Heading2"])
    bullet = lambda *text: paragraph(*text, style=ids["ListParagraph"], num=BULLETS)
    step = lambda *text: paragraph(*text, style=ids["ListParagraph"], num=STEPS)
    body = "".join([
        paragraph("Maintenance Procedure — KP-40 Hydraulic Pump", style=ids["Title"]),
        paragraph("Document KP-MP-012, revision B, approved by the workshop manager."),            # A → B

        h1("Purpose"),
        paragraph("This procedure describes the periodic maintenance of the KP-40 hydraulic pump installed on the test "
                  "benches of buildings C and D."),                                                # "and D"
        paragraph("It applies to every technician who has completed the basic hydraulics training and holds a valid "
                  "workshop badge."),
        paragraph(picture("rIdPump", 180, 105, "The KP-40 pump: its body, motor and outlet", 1),
                  extra='<w:jc w:val="center"/>'),
        paragraph(run("Figure 1: the KP-40 hydraulic pump.", i=True), extra='<w:jc w:val="center"/>'),
        paragraph("It replaces revision A, which no longer applies after 1 December 2026."),    # added

        h1("Scope"),
        paragraph("The following operations are covered:"),
        bullet("Visual inspection of the pump housing, hoses and fittings"),                       # changed
        bullet("Replacement of the suction filter"),
        bullet("Check of the oil level and color"),                                                # colour → color
        bullet("Measurement of the outlet pressure"),
        bullet("Cleaning of the cooling fins"),                                                    # added
        paragraph("Repairs of the motor and of the electrical cabinet are not covered by this document;  they are "
                  "described in procedure KP-MP-020. "),                                           # blanks only

        # "Procedure" now comes before "Safety": the two sections swapped places
        h1("Procedure"),
        step("Stop the pump and lock out the main switch."),
        step("Place a drip tray under the filter housing."),
        step("Open the drain valve and let the oil flow into the tray."),                          # added
        step("Unscrew the filter cover with the KP-T-05 strap wrench."),                           # changed
        step("Remove the old filter element and discard it with the oily waste."),
        step("Fit a new element, reference KP-F-110, and screw the cover back",
             inserted(" and tighten it to 25 N·m"), "."),                                     # tracked insertion
        step(WORD_PAGE, "Restart the pump, wait two minutes and check that no leak appears around the cover."),
        paragraph("The parts needed for one maintenance are listed below.",
                  extra='<w:ind w:left="720"/>'),                                                  # now indented
        table([["Part", "Reference", "Quantity"],
               ["Suction filter element", "KP-F-110", "1"],
               ["Cover seal", "KP-J-031", "1"],
               ["Drain valve seal", "KP-J-034", "1"],                                              # added row
               ["Hydraulic oil ISO VG 46", "KP-H-046", "3 litres"]], ids["TableGrid"],             # 2 → 3
              header_fill="D9E2F3", header_height=400,                                             # header shaded, taller
              borders={(4, 2): 12}),                                                               # a red border under 3 litres

        paragraph(WORD_PAGE, "Safety", style=ids["Heading1"], extra="<w:pageBreakBefore/>"),               # a new page
        paragraph("Before any operation, the pump must be stopped, isolated and locked out with a personal padlock."),
        paragraph("The residual pressure in the circuit can reach 15 bar for several minutes after the stop",
                  footnote_reference(1, ids["FootnoteReference"]),
                  "; wait at least fifteen minutes before opening any connection."),               # 12 → 15, ten → fifteen
        paragraph(run("Wear safety glasses and oil-resistant gloves at all times.", i=True)),      # now in italics
        paragraph(),                                                                               # an empty paragraph

        paragraph('<w:r><w:br w:type="page"/></w:r>'),                                          # a page break, alone
        h2("Records"),                                                                             # heading 1 → 2
        paragraph("Record the date, the outlet pressure and your name in the maintenance log kept ",
                  deleted("next to the bench"), inserted("in the workshop office"),                # tracked replacement
                  ". A pressure below 180 bar ", run("must", u=True),                              # bold → underlined
                  " be reported to the workshop manager the same day."),
    ])
    write_docx("procedure-revB.docx", title="Maintenance Procedure KP-MP-012", author="M. Laurent", body=body,
               styles=styles_part(ids, "Calibri", 22, "1F3864", 48),
               header=[paragraph(picture("rIdLogo", 18, 18, "Keldar's logo", 9),
                                " Keldar Pumps — Maintenance Procedure KP-MP-012", style=ids["Header"])],
               footer=[page_number(ids["Footer"])("Revision B — Page ")],                     # A → B
               footnotes=[(1, "Measured on the benches of buildings C and D in March 2026.")],     # changed
               template_link="file:///\\\\fileserver\\templates\\Keldar-Procedure-2026.dotx",      # another template
               template_name="Keldar-Procedure-2026.dotx", ids=ids,
               images={"Pump": ("pump.png", PUMP_REDRAWN)},                                     # the figure redrawn
               header_images={"Logo": ("logo.png", LOGO)})


# ---------------------------------------------------------------------------------------------------------------------
# The training handbook: another document, which copied paragraphs from revision A

def training_handbook():
    """
    Another template (other fonts and colours, styles under the ids a French Word gives them), other headings, its own
    paragraphs, and paragraphs copied from revision A of the procedure, in another order, with pieces changed: each
    copy says where it comes from.
    """
    ids = {"Normal": "Normal", "Title": "Titre", "Heading1": "Titre1", "Heading2": "Titre2",
           "ListParagraph": "Paragraphedeliste", "FootnoteText": "Notedebasdepage",
           "FootnoteReference": "Appelnotedebasdep", "Header": "En-tte", "Footer": "Pieddepage",
           "TableGrid": "Grilledutableau"}
    h1 = lambda text: paragraph(text, style=ids["Heading1"])
    h2 = lambda text: paragraph(text, style=ids["Heading2"])
    bullet = lambda *text: paragraph(*text, style=ids["ListParagraph"], num=BULLETS)
    step = lambda *text: paragraph(*text, style=ids["ListParagraph"], num=STEPS)
    question = lambda *text: paragraph(*text, style=ids["ListParagraph"], num=STEPS_AGAIN)
    body = "".join([
        paragraph("Workshop Training Handbook", style=ids["Title"]),
        h1("Module 3: Looking after the KP-40 pump"),
        paragraph("Trainer: J. Petit. Duration: half a day, on the bench, in groups of four."),

        h2("Why it matters"),
        paragraph("A clogged filter is the first cause of pump failures on our benches: changing it on time saves days "
                  "of downtime."),
        # copied from "Purpose": procedure → module
        paragraph("This module describes the periodic maintenance of the KP-40 hydraulic pump installed on the test "
                  "benches of building C."),
        # copied from "Purpose": the figure, as it is
        paragraph(picture("rIdPump", 180, 105, "The KP-40 pump: its body, motor and outlet", 1),
                  extra='<w:jc w:val="center"/>'),

        h2("Before you start"),
        # copied from "Safety", the second and third sentences changed, the footnote left behind
        paragraph("Before any operation, the pump must be stopped, isolated and locked out with your own padlock."),
        paragraph("Wear safety glasses and oil-resistant gloves at all times."),                   # copied as is
        paragraph("The residual pressure in the circuit can reach 12 bar for several minutes after the stop; always "
                  "wait at least ten minutes before opening any connection."),

        h2("Changing the filter"),
        paragraph("Follow the steps below; your trainer checks each one before you go on to the next."),
        # copied from "Procedure", some steps reworded, the last one left out
        step("Stop the pump and lock out the main switch."),
        step("Place a drip tray under the filter housing."),
        step("Unscrew the cover of the filter by hand; never use a tool."),
        step("Remove the old filter element and discard it with the oily waste."),
        step("Fit a new element (reference KP-F-110) and screw the cover back."),
        # copied from "Procedure": the table without its quantities
        table([["Part", "Reference"],
               ["Suction filter element", "KP-F-110"],
               ["Cover seal", "KP-J-031"]], ids["TableGrid"]),

        h2("What to write down"),
        # copied from "Records", its second sentence rewritten
        paragraph("Record the date, the outlet pressure and your name in the maintenance log kept next to the bench. "
                  "Report any pressure below 180 bar to your trainer the same day."),
        # copied from "Scope": the list of operations, now a paragraph
        paragraph("The module does not cover the repairs of the motor and of the electrical cabinet; they are "
                  "described in procedure KP-MP-020."),

        h2("Quiz"),
        question("Why must you wait before opening a connection?"),
        question("Where do you record the outlet pressure?"),
        question("Which reference has the suction filter element?"),
    ])
    write_docx("training-handbook.docx", title="Workshop Training Handbook, module 3", author="J. Petit", body=body,
               styles=styles_part(ids, "Georgia", 24, "7B2C00", 52),
               header=[paragraph("Workshop Training Handbook — Module 3", style=ids["Header"])],
               footer=[page_number(ids["Footer"])("Training — ")],
               footnotes=[],
               template_link="file:///\\\\fileserver\\templates\\Keldar-Training.dotx",
               template_name="Keldar-Training.dotx", ids=ids,
               images={"Pump": ("pump.png", PUMP)})


if __name__ == "__main__":
    procedure_revision_a()
    procedure_revision_b()
    training_handbook()
