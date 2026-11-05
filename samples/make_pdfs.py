#!/usr/bin/env python3
"""
Writes the sample PDFs of samples/, to try DocComparator's comparison of PDFs on (README, "Samples"):

  procedure-revA.pdf   procedure-revA.docx printed to PDF
  procedure-revB.pdf   procedure-revB.docx printed to PDF: the same changes to find as between the two documents,
                       their pages laid out again (a section moved, steps added, a table row inserted)

Each is saved as PDF by LibreOffice from the Word sample (samples/make_samples.py writes those), so that the PDFs
are an office suite's own, not hand-made: LibreOffice must be installed (soffice). LibreOffice dates what it
writes: the files change at each run, their content does not. The words each holds, as Poppler's pdftotext reads
them, are written beside PdfSamplesTest, which checks that the program reads the same (pdftotext must be installed).

    python3 samples/make_pdfs.py
"""

import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
WORDS = os.path.join(HERE, "..", "doccomparator-core", "src", "test", "resources", "org", "jtools", "doccomparator",
                     "pdf")


def main():
    with tempfile.TemporaryDirectory() as work:
        profile = "file://" + os.path.join(work, "profile")   # LibreOffice's settings of this run, not the user's
        documents = [os.path.join(HERE, "procedure-rev%s.docx" % rev) for rev in ("A", "B")]
        subprocess.run(["soffice", "-env:UserInstallation=" + profile, "--headless", "--convert-to", "pdf",
                        "--outdir", HERE] + documents, check=True)
    for rev in ("A", "B"):
        text = subprocess.run(["pdftotext", "-enc", "UTF-8", os.path.join(HERE, "procedure-rev%s.pdf" % rev), "-"],
                              check=True, capture_output=True, text=True, encoding="utf-8").stdout
        with open(os.path.join(WORDS, "procedure-rev%s.words" % rev), "w", encoding="utf-8") as out:
            out.write("".join(word + "\n" for word in sorted(set(text.split()))))


if __name__ == "__main__":
    sys.exit(main())
