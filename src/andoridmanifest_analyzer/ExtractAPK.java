/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package andoridmanifest_analyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.JTextPane;

public class ExtractAPK {

    List<String> fileList;
    private String xmlFile = null;

    public ExtractAPK()
    {
        xmlFile = "";
    }
    /**
     * Unzip it
     *
     * @param zipFile input zip file
     * @param output zip file output folder
     */
    public void unZipIt(JTextPane txtLog, String zipFile, String outputFolder) {

        byte[] buffer = new byte[1024];

        try {

            //create output directory is not exists
            File folder = new File(outputFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }

            //get the zip file content
            ZipInputStream zis =
                    new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {

                String fileName = ze.getName();
                File newFile = new File(outputFolder + File.separator + fileName);

                txtLog.setText(txtLog.getText() + "file unzip : " + newFile.getAbsoluteFile() + "\n");

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                FileOutputStream fos = new FileOutputStream(newFile);

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();

            txtLog.setText(txtLog.getText() + "Done\n");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    // decompressXML -- Parse the 'compressed' binary form of Android XML docs 
// such as for AndroidManifest.xml in .apk files
    public static int endDocTag = 0x00100101;
    public static int startTag = 0x00100102;
    public static int endTag = 0x00100103;

    public String decompressXML(byte[] xml) {
// Compressed XML file/bytes starts with 24x bytes of data,
// 9 32 bit words in little endian order (LSB first):
//   0th word is 03 00 08 00
//   3rd word SEEMS TO BE:  Offset at then of StringTable
//   4th word is: Number of strings in string table
// WARNING: Sometime I indiscriminently display or refer to word in 
//   little endian storage format, or in integer format (ie MSB first).
        int numbStrings = LEW(xml, 4 * 4);

// StringIndexTable starts at offset 24x, an array of 32 bit LE offsets
// of the length/string data in the StringTable.
        int sitOff = 0x24;  // Offset of start of StringIndexTable

// StringTable, each string is represented with a 16 bit little endian 
// character count, followed by that number of 16 bit (LE) (Unicode) chars.
        int stOff = sitOff + numbStrings * 4;  // StringTable follows StrIndexTable

// XMLTags, The XML tag tree starts after some unknown content after the
// StringTable.  There is some unknown data after the StringTable, scan
// forward from this point to the flag for the start of an XML start tag.
        int xmlTagOff = LEW(xml, 3 * 4);  // Start from the offset in the 3rd word.
// Scan forward until we find the bytes: 0x02011000(x00100102 in normal int)
        for (int ii = xmlTagOff; ii < xml.length - 4; ii += 4) {
            if (LEW(xml, ii) == startTag) {
                xmlTagOff = ii;
                break;
            }
        } // end of hack, scanning for start of first start tag

// XML tags and attributes:
// Every XML start and end tag consists of 6 32 bit words:
//   0th word: 02011000 for startTag and 03011000 for endTag 
//   1st word: a flag?, like 38000000
//   2nd word: Line of where this tag appeared in the original source file
//   3rd word: FFFFFFFF ??
//   4th word: StringIndex of NameSpace name, or FFFFFFFF for default NS
//   5th word: StringIndex of Element Name
//   (Note: 01011000 in 0th word means end of XML document, endDocTag)

// Start tags (not end tags) contain 3 more words:
//   6th word: 14001400 meaning?? 
//   7th word: Number of Attributes that follow this tag(follow word 8th)
//   8th word: 00000000 meaning??

// Attributes consist of 5 words: 
//   0th word: StringIndex of Attribute Name's Namespace, or FFFFFFFF
//   1st word: StringIndex of Attribute Name
//   2nd word: StringIndex of Attribute Value, or FFFFFFF if ResourceId used
//   3rd word: Flags?
//   4th word: str ind of attr value again, or ResourceId of value

// TMP, dump string table to tr for debugging
//tr.addSelect("strings", null);
//for (int ii=0; ii<numbStrings; ii++) {
//  // Length of string starts at StringTable plus offset in StrIndTable
//  String str = compXmlString(xml, sitOff, stOff, ii);
//  tr.add(String.valueOf(ii), str);
//}
//tr.parent();

// Step through the XML tree element tags and attributes
        int off = xmlTagOff;
        int indent = 0;
        int startTagLineNo = -2;
        while (off < xml.length) {
            int tag0 = LEW(xml, off);
            //int tag1 = LEW(xml, off+1*4);
            int lineNo = LEW(xml, off + 2 * 4);
            //int tag3 = LEW(xml, off+3*4);
            int nameNsSi = LEW(xml, off + 4 * 4);
            int nameSi = LEW(xml, off + 5 * 4);

            if (tag0 == startTag) { // XML START TAG
                int tag6 = LEW(xml, off + 6 * 4);  // Expected to be 14001400
                int numbAttrs = LEW(xml, off + 7 * 4);  // Number of Attributes to follow
                //int tag8 = LEW(xml, off+8*4);  // Expected to be 00000000
                off += 9 * 4;  // Skip over 6+3 words of startTag data
                String name = compXmlString(xml, sitOff, stOff, nameSi);
                //tr.addSelect(name, null);
                startTagLineNo = lineNo;

                // Look for the Attributes
                StringBuffer sb = new StringBuffer();
                for (int ii = 0; ii < numbAttrs; ii++) {
                    int attrNameNsSi = LEW(xml, off);  // AttrName Namespace Str Ind, or FFFFFFFF
                    int attrNameSi = LEW(xml, off + 1 * 4);  // AttrName String Index
                    int attrValueSi = LEW(xml, off + 2 * 4); // AttrValue Str Ind, or FFFFFFFF
                    int attrFlags = LEW(xml, off + 3 * 4);
                    int attrResId = LEW(xml, off + 4 * 4);  // AttrValue ResourceId or dup AttrValue StrInd
                    off += 5 * 4;  // Skip over the 5 words of an attribute
                    String attrName = null ;
                    if(compXmlString(xml, sitOff, stOff, attrNameSi).equalsIgnoreCase("package"))
                        attrName = compXmlString(xml, sitOff, stOff, attrNameSi);
                    else
                        attrName = "android:"+compXmlString(xml, sitOff, stOff, attrNameSi);
                    
                    String attrValue = attrValueSi != -1
                            ? compXmlString(xml, sitOff, stOff, attrValueSi)
                            : ""+attrResId;
                    
                    sb.append(" " + attrName + "=\"" + attrValue + "\"");
                    //tr.add(attrName, attrValue);
                }
                prtIndent(indent, "<" + name + sb + ">");
                indent++;

            } else if (tag0 == endTag) { // XML END TAG
                indent--;
                off += 6 * 4;  // Skip over 6 words of endTag data
                String name = compXmlString(xml, sitOff, stOff, nameSi);
                prtIndent(indent, "</" + name + "> \n");
                //tr.parent();  // Step back up the NobTree

            } else if (tag0 == endDocTag) {  // END OF XML DOC TAG
                break;

            } else {
                System.out.println("  Unrecognized tag code '" + Integer.toHexString(tag0)
                        + "' at offset " + off);
                break;
            }
        } // end of while loop scanning tags and attributes of XML tree
        //System.out.println("    end at offset " + off);
        return xmlFile;
    } // end of decompressXML

    public String compXmlString(byte[] xml, int sitOff, int stOff, int strInd) {
        if (strInd < 0) {
            return null;
        }
        int strOff = stOff + LEW(xml, sitOff + strInd * 4);
        return compXmlStringAt(xml, strOff);
    }
    public static String spaces = "                                             ";

    public void prtIndent(int indent, String str) {
       // System.out.println(spaces.substring(0, Math.min(indent * 2, spaces.length())) + str);
        xmlFile +=spaces.substring(0, Math.min(indent * 2, spaces.length())) + str;
    }

// compXmlStringAt -- Return the string stored in StringTable format at
// offset strOff.  This offset points to the 16 bit string length, which 
// is followed by that number of 16 bit (Unicode) chars.
    public String compXmlStringAt(byte[] arr, int strOff) {
        int strLen = arr[strOff + 1] << 8 & 0xff00 | arr[strOff] & 0xff;
        byte[] chars = new byte[strLen];
        for (int ii = 0; ii < strLen; ii++) {
            chars[ii] = arr[strOff + 2 + ii * 2];
        }
        return new String(chars);  // Hack, just use 8 byte chars
    } // end of compXmlStringAt

// LEW -- Return value of a Little Endian 32 bit word from the byte array
//   at offset off.
    public int LEW(byte[] arr, int off) {
        return arr[off + 3] << 24 & 0xff000000 | arr[off + 2] << 16 & 0xff0000
                | arr[off + 1] << 8 & 0xff00 | arr[off] & 0xFF;
    } // end of LEW
}