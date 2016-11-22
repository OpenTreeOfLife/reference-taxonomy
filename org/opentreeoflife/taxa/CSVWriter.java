/*
  http://opencsv.sourceforge.net/ :

  CSVWriter writer = new CSVWriter(new FileWriter("yourfile.csv"), '\t');
     // feed in your array (or convert your data to an array)
     String[] entries = "first#second#third".split("#");
     writer.writeNext(entries);
	 writer.close();
*/

package org.opentreeoflife.taxa;

import java.io.Writer;
import java.io.IOException;

public class CSVWriter {

    Writer writer;

    public CSVWriter(Writer writer) {
        this.writer = writer;
    }

    static final char SEP = ',';
    static final char QUOTE = '"';
    static final String QUOTE_STRING = new String(new char[]{QUOTE});
    static final String QUOTE_QUOTE_STRING = new String(new char[]{QUOTE, QUOTE});

    public void writeNext(String[] fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        int nfields = fields.length;
        for (int i = 0; i < nfields; ++i) {
            if (i > 0)
                sb.append(SEP);
            if (fields[i] == null)
                ;
            else if (fields[i].indexOf(SEP) >= 0 || fields[i].indexOf(QUOTE) >= 0) {
                sb.append(QUOTE);
                sb.append(fields[i].replace(QUOTE_STRING, QUOTE_QUOTE_STRING));
                sb.append(QUOTE);
            } else
                sb.append(fields[i]);
        }
        sb.append('\n');
        writer.write(sb.toString());
    }

    public void close() throws IOException {
        writer.close();
    }

}
