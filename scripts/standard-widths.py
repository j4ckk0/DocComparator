#!/usr/bin/env python3
"""
Writes doccomparator-core/src/main/resources/org/jtools/doccomparator/pdf/standard-widths.txt: the widths of the
characters of the PDF standard fonts — Helvetica and Times, each in its four styles — that a PDF may use without
giving them (ISO 32000-2, 9.6.2.2), as the program needs them to know where a word ends. Courier needs none: all its
characters are 600 wide.

The widths are those of the URW base 35 fonts, made to the same metrics (Nimbus Sans for Helvetica, Nimbus Roman for
Times), read from their AFM files (Debian's fonts-urw-base35 package). A line a font: its name, then the widths of
the characters of WinAnsiEncoding's codes 32 to 255, in thousandths of the size, 0 for a code without a character.

    python3 scripts/standard-widths.py
"""

import os

AFM = '/usr/share/fonts/type1/urw-base35'
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'doccomparator-core', 'src', 'main', 'resources', 'org', 'jtools', 'doccomparator', 'pdf',
                   'standard-widths.txt')
FONTS = [('Helvetica', 'NimbusSans-Regular'), ('Helvetica-Bold', 'NimbusSans-Bold'),
         ('Helvetica-Oblique', 'NimbusSans-Italic'), ('Helvetica-BoldOblique', 'NimbusSans-BoldItalic'),
         ('Times-Roman', 'NimbusRoman-Regular'), ('Times-Bold', 'NimbusRoman-Bold'),
         ('Times-Italic', 'NimbusRoman-Italic'), ('Times-BoldItalic', 'NimbusRoman-BoldItalic')]

# WinAnsiEncoding's glyph names, codes 32 to 255 (ISO 32000-2, annex D.2); None where it has none
ASCII = ('space exclam quotedbl numbersign dollar percent ampersand quotesingle parenleft parenright asterisk plus '
         'comma hyphen period slash zero one two three four five six seven eight nine colon semicolon less equal '
         'greater question at A B C D E F G H I J K L M N O P Q R S T U V W X Y Z bracketleft backslash '
         'bracketright asciicircum underscore grave a b c d e f g h i j k l m n o p q r s t u v w x y z braceleft '
         'bar braceright asciitilde bullet').split()
HIGH = ('Euro bullet quotesinglbase florin quotedblbase ellipsis dagger daggerdbl circumflex perthousand Scaron '
        'guilsinglleft OE bullet Zcaron bullet bullet quoteleft quoteright quotedblleft quotedblright bullet endash '
        'emdash tilde trademark scaron guilsinglright oe bullet zcaron Ydieresis space exclamdown cent sterling '
        'currency yen brokenbar section dieresis copyright ordfeminine guillemotleft logicalnot hyphen registered '
        'macron degree plusminus twosuperior threesuperior acute mu paragraph periodcentered cedilla onesuperior '
        'ordmasculine guillemotright onequarter onehalf threequarters questiondown Agrave Aacute Acircumflex Atilde '
        'Adieresis Aring AE Ccedilla Egrave Eacute Ecircumflex Edieresis Igrave Iacute Icircumflex Idieresis Eth '
        'Ntilde Ograve Oacute Ocircumflex Otilde Odieresis multiply Oslash Ugrave Uacute Ucircumflex Udieresis '
        'Yacute Thorn germandbls agrave aacute acircumflex atilde adieresis aring ae ccedilla egrave eacute '
        'ecircumflex edieresis igrave iacute icircumflex idieresis eth ntilde ograve oacute ocircumflex otilde '
        'odieresis divide oslash ugrave uacute ucircumflex udieresis yacute thorn ydieresis').split()
NAMES = ASCII + HIGH
assert len(NAMES) == 224, len(NAMES)
# the codes Windows-1252 leaves without a character: none in the program either
UNDEFINED = {0x7F, 0x81, 0x8D, 0x8F, 0x90, 0x9D}


def widths(afm):
    found = {}
    with open(os.path.join(AFM, afm + '.afm'), encoding='latin-1') as f:
        for line in f:
            if line.startswith('C '):
                fields = dict(part.strip().split(' ', 1) for part in line.split(';') if part.strip())
                found[fields['N']] = int(float(fields['WX']))
    return found


def main():
    lines = []
    for name, afm in FONTS:
        known = widths(afm)
        row = [0 if 32 + i in UNDEFINED else known.get(glyph, 0) for i, glyph in enumerate(NAMES)]
        lines.append(name + ' ' + ' '.join(map(str, row)))
    with open(OUT, 'w', encoding='ascii') as f:
        f.write('# Written by scripts/standard-widths.py: WinAnsiEncoding codes 32 to 255, thousandths of the size\n')
        f.write('\n'.join(lines) + '\n')
    print('written', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
