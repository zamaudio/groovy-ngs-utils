import groovy.transform.CompileStatic;
/**
 * A representation of a genomic position as a long
 * 
 * The first 11 digits are the position within a contig / chromosome,
 * while the leading 2 digits define the chromosome.
 * 
 * @author simon
 */
class XPos {
    
    @CompileStatic
    public static long computePos(String chr, int pos) {
        String contigNoChr = chr.startsWith('chr') ? chr.substring(3) : chr
        computeId(chrToInt(contigNoChr), pos)
    }
    
    @CompileStatic
    public static int chrToInt(String chr) {
        switch(chr) {
            case "X":
                return 22
            case "Y":
                return 23
            default:
                return chr.toInteger()-1
        }
    }
    
    static final String [] INT_CHRS = (
        (1..22).collect { String.valueOf(it) } + ["X","Y"]
        ) as String[]
    
    @CompileStatic
    public static String intToChr(int chrInt) {
        INT_CHRS[chrInt]    
    }
    
    @CompileStatic
    public static Region parseId(long id) {
        int chrInt = (int)(id / 1000000000L)
        int pos = (int)(id - chrInt * 1000000000L)
        new Region(intToChr(chrInt), pos..pos)
    }
    
    @CompileStatic
    public static long computeId(int chr, int pos) {
        // Absolute coordinate = chr * 10^e9 + pos
        chr * 1000000000L + pos
    }
}
