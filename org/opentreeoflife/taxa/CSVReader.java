/*
  CSVReader reader = new CSVReader(new FileReader("yourfile.csv"), '\t');
     // feed in your array (or convert your data to an array)
     String[] entries = "first#second#third".split("#");
     reader.writeNext(entries);
	 reader.close();
*/

package org.opentreeoflife.taxa;

import java.io.Reader;
import java.io.IOException;
import java.io.Closeable;
import java.util.regex.Pattern;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

// s.nextLine()


// having this implement Iterable<String[]> would be nice...

public class CSVReader implements Closeable {

    Reader reader;
    Scanner scanner;

    public CSVReader(Reader reader) {
        this.reader = reader;
        this.scanner = new Scanner(reader);
    }

    static final char SEP = ',';
    static final char QUOTE = '\'';

    public String[] readNext() throws IOException {
        if (!scanner.hasNext()) return null;
        String line = scanner.nextLine();
        if (line == null) return null;

        int i = 0;
        int stop = line.length();
        List<String> result = new ArrayList<String>();
        StringBuilder bu = new StringBuilder();
        while (i < stop) {
            char c = line.charAt(i++);
            if (c == SEP) {
                result.add(bu.toString());
                bu.setLength(0);
            } else if (c == QUOTE)
                while (i < stop) {
                    c = line.charAt(i++);
                    if (c == QUOTE) {
                        if ((i+1) < stop && line.charAt(i+1) == QUOTE)
                            i++;
                        else
                            break;
                    }
                    bu.append(c);
                }
            else
                bu.append(c);
        }
        result.add(bu.toString());
        return result.toArray(dummy);
    }

    private final String[] dummy = new String[]{};

    public void close() throws IOException {
        reader.close();
    }

    public static void main(String[] args) throws Exception {
        CSVReader r = new CSVReader(new java.io.StringReader("a,b,c\nc,b,a\nd,'e',f\ng,'h''h',i"));
        String[] row = null;
        while (true) {
            row = r.readNext();
            if (row == null) break;
            for (int i = 0; i < row.length; ++i) {
                if (i > 0)
                    System.out.print(" | ");
                System.out.print(row[i]);
            }
            System.out.println();
        }
    }

}
