#!/usr/bin/env python3
"""
Writes the sample workbooks of samples/, to try DocComparator's comparison of Excel workbooks on (README, "Samples"):

  budget-revA.xlsx   a workshop budget: a merged title, a shaded header, costs (#,##0.00), their shares (0.0%, as
                     formulas), order dates, a total (SUM), and a second sheet, the steps of a planning
  budget-revB.xlsx   its next version: a cost changed, a row inserted, the total by SUBTOTAL (the same result), the
                     header shaded otherwise, the total in italics, the columns wider, two steps swapped

Each is written as an OpenDocument flat file (.fods), then saved as .xlsx by LibreOffice, so that the workbooks are
an office suite's own, not hand-made: LibreOffice must be installed (soffice). LibreOffice dates what it writes: the
files change at each run, their content does not.

    python3 samples/make_workbooks.py
"""

import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
NS = ('xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" '
      'xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" '
      'xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" '
      'xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" '
      'xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" '
      'xmlns:number="urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0" '
      'xmlns:of="urn:oasis:names:tc:opendocument:xmlns:of:1.2"')

def styles(rev):
    head_fill = '#d9e2f3' if rev == 'A' else '#fce4d6'
    return f'''<office:automatic-styles>
 <number:number-style style:name="N2"><number:number number:decimal-places="2" number:min-decimal-places="2" number:min-integer-digits="1" number:grouping="true"/></number:number-style>
 <number:percentage-style style:name="NP"><number:number number:decimal-places="1" number:min-decimal-places="1" number:min-integer-digits="1"/><number:text>%</number:text></number:percentage-style>
 <number:date-style style:name="ND"><number:day number:style="long"/><number:text>/</number:text><number:month number:style="long"/><number:text>/</number:text><number:year number:style="long"/></number:date-style>
 <style:style style:name="coA" style:family="table-column"><style:table-column-properties style:column-width="4.5cm"/></style:style>
 <style:style style:name="coB" style:family="table-column"><style:table-column-properties style:column-width="{'2.5cm' if rev == 'A' else '3.2cm'}"/></style:style>
 <style:style style:name="title" style:family="table-cell"><style:table-cell-properties fo:background-color="#1f4e79"/><style:paragraph-properties fo:text-align="center"/><style:text-properties fo:font-weight="bold" fo:font-size="14pt" fo:color="#ffffff"/></style:style>
 <style:style style:name="head" style:family="table-cell"><style:table-cell-properties fo:background-color="{head_fill}" fo:border-bottom="1.5pt solid #000000"/><style:text-properties fo:font-weight="bold"/></style:style>
 <style:style style:name="money" style:family="table-cell" style:data-style-name="N2"/>
 <style:style style:name="pct" style:family="table-cell" style:data-style-name="NP"/>
 <style:style style:name="date" style:family="table-cell" style:data-style-name="ND"/>
 <style:style style:name="total" style:family="table-cell" style:data-style-name="N2"><style:table-cell-properties fo:border-top="0.75pt solid #000000"/><style:text-properties fo:font-weight="bold"{' fo:font-style="italic"' if rev == 'B' else ''}/></style:style>
</office:automatic-styles>'''

def s(text, st=None):
    a = f' table:style-name="{st}"' if st else ''
    return f'<table:table-cell{a} office:value-type="string"><text:p>{text}</text:p></table:table-cell>'
def f(v, st=None, formula=None):
    a = f' table:style-name="{st}"' if st else ''
    fo = f' table:formula="of:={formula}"' if formula else ''
    return f'<table:table-cell{a}{fo} office:value-type="float" office:value="{v}"><text:p>{v}</text:p></table:table-cell>'
def p(v, formula=None):
    fo = f' table:formula="of:={formula}"' if formula else ''
    return f'<table:table-cell table:style-name="pct"{fo} office:value-type="percentage" office:value="{v}"><text:p>{v}</text:p></table:table-cell>'
def d(iso):
    return f'<table:table-cell table:style-name="date" office:value-type="date" office:date-value="{iso}"><text:p>{iso}</text:p></table:table-cell>'
def row(*cells):
    return '<table:table-row>' + ''.join(cells) + '</table:table-row>'

def budget(rev):
    items = [('Hydraulic pump KP-40', 1250.0, '2026-03-02'), ('Suction filter', 85.5, '2026-03-02'),
             ('Hoses and seals', 342.75, '2026-03-09')]
    if rev == 'B':
        items[1] = ('Suction filter', 92.0, '2026-03-02')                      # a value changed
        items.insert(2, ('Pressure gauge', 128.4, '2026-03-05'))                # a row inserted
    rows = [row('<table:table-cell table:style-name="title" table:number-columns-spanned="4" office:value-type="string"><text:p>Workshop budget 2026</text:p></table:table-cell>',
                '<table:covered-table-cell/>' * 3)]
    rows.append(row(s('Item', 'head'), s('Cost', 'head'), s('Share', 'head'), s('Ordered', 'head')))
    n = len(items)
    for i, (name, cost, when) in enumerate(items):
        r = i + 3
        rows.append(row(s(name), f(cost, 'money'), p(round(cost / sum(c for _, c, _ in items), 6), f'[.B{r}]/[.B{n + 3}]'), d(when)))
    total = 'SUM([.B3:.B%d])' % (n + 2) if rev == 'A' else 'SUBTOTAL(9;[.B3:.B%d])' % (n + 2)   # a formula changed, same result
    rows.append(row(s('Total', 'total'), f(sum(c for _, c, _ in items), 'total', total)))
    cols = '<table:table-column table:style-name="coA"/><table:table-column table:style-name="coB" table:number-columns-repeated="3"/>'
    return f'<table:table table:name="Budget">{cols}' + ''.join(rows) + '</table:table>'

def planning(rev):
    steps = ['Isolate the pump', 'Drain the circuit', 'Replace the filter', 'Check the pressure']
    if rev == 'B':
        steps[1], steps[2] = steps[2], steps[1]                                  # rows moved
    rows = [row(s('Step', 'head'), s('Action', 'head'))]
    for i, step in enumerate(steps):
        rows.append(row(f(i + 1), s(step)))
    return '<table:table table:name="Planning"><table:table-column table:style-name="coA" table:number-columns-repeated="2"/>' + ''.join(rows) + '</table:table>'

def main():
    with tempfile.TemporaryDirectory() as work:
        flat = []
        for rev in ("A", "B"):
            path = os.path.join(work, "budget-rev%s.fods" % rev)
            with open(path, "w", encoding="utf-8") as out:
                out.write('<?xml version="1.0" encoding="UTF-8"?><office:document ' + NS + ' office:version="1.3" '
                          'office:mimetype="application/vnd.oasis.opendocument.spreadsheet">' + styles(rev)
                          + '<office:body><office:spreadsheet>' + budget(rev) + planning(rev)
                          + '</office:spreadsheet></office:body></office:document>')
            flat.append(path)
        profile = "file://" + os.path.join(work, "profile")   # LibreOffice's settings of this run, not the user's
        subprocess.run(["soffice", "-env:UserInstallation=" + profile, "--headless", "--convert-to", "xlsx",
                        "--outdir", HERE] + flat, check=True)


if __name__ == "__main__":
    sys.exit(main())
