# Using DocComparator's libraries

Three small programs, in [`src/main/java/org/jtools/doccomparator/examples/`](src/main/java/org/jtools/doccomparator/examples/).
This folder is a module of the build (`doccomparator-examples`, never published): the programs compile with every
build, and `ExamplesTest` runs `CompareFiles` and `CsvReader` on the samples.

| Example | Jar it needs | What it shows |
|---|---|---|
| [`CompareFiles`](src/main/java/org/jtools/doccomparator/examples/CompareFiles.java) | `doccomparator-core` | compare two files without a window, and print what differs, in words |
| [`CsvReader`](src/main/java/org/jtools/doccomparator/examples/CsvReader.java) | `doccomparator-core` | add a format: CSV files, read as a table and compared row by row and column by column |
| [`EmbedPanel`](src/main/java/org/jtools/doccomparator/examples/EmbedPanel.java) | `doccomparator-core`, `doccomparator-ui` | show the comparison view in a window of your own, one side a file, the other a document held in memory |

## Running them

Build once, from the project's root (`scripts/build.sh`, or `mvn package`), then, from the root:

```bash
CORE=doccomparator-core/target/doccomparator-core.jar
UI=doccomparator-ui/target/doccomparator-ui.jar
EXAMPLES=examples/target/doccomparator-examples.jar

java -cp $CORE:$EXAMPLES org.jtools.doccomparator.examples.CompareFiles samples/procedure-revA.docx samples/procedure-revB.docx
java -cp $CORE:$EXAMPLES org.jtools.doccomparator.examples.CompareFiles samples/budget-revA.xlsx samples/budget-revB.xlsx --all
java -cp $CORE:$EXAMPLES org.jtools.doccomparator.examples.CsvReader old.csv new.csv
java -cp $CORE:$UI:$EXAMPLES org.jtools.doccomparator.examples.EmbedPanel samples/procedure-revA.docx
```

Each is a single file, so Java can also run it from its source, with only the libraries' jars:

```bash
java -cp $CORE examples/src/main/java/org/jtools/doccomparator/examples/CompareFiles.java samples/procedure-revA.docx samples/procedure-revB.docx
```

In IntelliJ IDEA or another IDE that imports the Maven build, run them from their `main`, the project's root as the
working directory.

On Windows, the class path's separator is `;` rather than `:`.

## In a project of your own

Once on Maven Central (see [PUBLISHING.md](../PUBLISHING.md)), with Maven:

```xml
<dependency>
  <groupId>org.jtools</groupId>
  <artifactId>doccomparator-core</artifactId>   <!-- or doccomparator-ui, which brings core -->
  <version>2.1</version>
</dependency>
```

As Java modules: `requires org.jtools.doccomparator.core;` or `requires org.jtools.doccomparator.ui;`.

## What to start from

- **Reading**: `DocumentReaders.forFile(path).read(path)` gives a `Document`, whatever the format; a file that cannot
  be read is a `DocumentReadException`, its message naming the file and saying why.
- **Comparing**: `DocumentComparer.compare(left, right, options)`, with `CompareOptions.TEXT_ONLY`, `DEFAULT`, `ALL`,
  or any of them `.with(Aspect.CHARACTERS, true)`. The `Comparison` holds, by part, the `Pairing`s of paragraphs and
  tables: each says whether it is the same, changed, moved, or on one side only.
- **Saying it**: `ComparisonWords` puts any of it into words, in the language `I18n` speaks (`I18n.use(...)`).
- **A format of your own**: extend `AbstractDocumentReader` and declare it as a service; your jar beside the
  libraries, `DocumentReaders` finds it, and the view's file chooser offers your files too. (The downloads of the
  program are closed: a reader is added to a program built on the libraries, not to them.)
- **Your own texts**: implement `I18nTexts` and declare it as a service, its files `messages_en.properties` and
  `messages_fr.properties` beside it; `I18n.text(key)` then finds your keys as well as the program's.
- **The view**: `ComparatorPanel` is a Swing component: `open(...)` two files or two `DocumentSource`s of your own,
  `onCompared(...)` to hear of each comparison, `fileActions()`, `goActions()`, `compareMenu()` and `viewMenu()` for
  your window's menus. It takes the look and feel in use.
