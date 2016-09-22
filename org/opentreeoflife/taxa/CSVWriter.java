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

    public void writeNext(String[] fields) throws IOException {
        StringBuffer sb = new StringBuffer();
        int nfields = fields.length;
        for (int i = 0; i < nfields; ++i) {
            if (i > 0)
                sb.append(',');
            if (fields[i] == null)
                ;
            else if (fields[i].indexOf(',') >= 0 || fields[i].indexOf('"') >= 0) {
                sb.append('"');
                sb.append(fields[i].replace("\"", "\"\""));
                sb.append('"');
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
