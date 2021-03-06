import gngs.BED
import gngs.Cli
import gngs.GRange
import gngs.IRegion
import gngs.RangedData
import gngs.Region
import gngs.Regions
import gngs.Utils
import gngs.VCF
import gngs.Variant

class TargetedCNVAnnotator {
    
    Regions target
    
    DGV dgv 
    
    String omimFile
    
    boolean verbose = false
    
    public TargetedCNVAnnotator(Regions targetRegions, RangedData dgv/*, String omimFile*/) {
        this.target = targetRegions
        this.dgv = new DGV(dgv)
    }
    
    public TargetedCNVAnnotator(Regions targetRegions, String dgvFile/*, String omimFile*/) {
        this.target = targetRegions
        this.dgv = new DGV(dgvFile).parse()
//        this.omimFile = omimFile
    }
    
   void annotate(String vcfFile, Writer output) {
	   
       VCF.filter(vcfFile) { Variant v ->
           
           // Output non-CNV variants unchanged
           if(!["GAIN","LOSS"].contains(v.type)) {
               return true   
           }
		   
		   def ann = annotate(v, v.type)
          
           v.update("CNV Known Variant Annotation") {
               if(ann.dgvCnvs) {
                   v.info.KCNV = ann.dgvCnvs.collect { cnv ->
                       int cnvCount = type == "GAIN" ? cnv.observedGains : cnv.observedLosses
                       [cnv.name, cnvCount, cnv.sampleSize, (float)cnvCount / cnv.sampleSize.toFloat()].join(":")
                   }.join(",")
               }
               v.info.SCNV = ann.spanning.size() + ":" + String.format("%.3f",ann.spanningFreq)
           }
          
           return true
       }
   }
   
   Map annotateSize(IRegion cnv) {
       
       Map result = [
           targets : this.target.getOverlaps(cnv).size(),
           targetBp : this.target.intersect(cnv).sum { it.size() }
       ]
       
       return result
   }
   
   Map annotate(IRegion v, String type) {
       
       // Make the type compatible with how we tag CNVs (DEL,DUP)
       if(type == "DEL")
           type = "LOSS"
       
       if(type == "DUP")
           type = "GAIN"
           
       if(!v.chr.startsWith('chr'))
           v = new Region('chr'+v.chr, v.range)
	   
       List<GRange> compatibleRanges = findCompatibleRanges(v)
           
       // These GRanges have Regions as extra's:
       List<Region> dgvCnvs = compatibleRanges*.extra
           
       // They must be the same type
       dgvCnvs = filterByType(type, dgvCnvs)
           
       // Look for "crossing" variants
       List<Region> spanning = dgv.getOverlaps(v.chr, v.range.from, v.range.to).grep { GRange r ->
           r.spans(v.range)  
       }*.extra
           
       if(type != "ANY")
           spanning = filterByType(type, spanning)
           
       float spanningFreq = spanning.grep { it.sampleSize>5}.collect { cnv ->
           int cnvCount = 0
           if(type == "GAIN")
               cnvCount = cnv.observedGains
           else
           if(type == "LOSS")
               cnvCount =  cnv.observedLosses
           else
           if(type == "ANY")
               cnvCount =  cnv.observedLosses + cnv.observedGains
               
           (float)cnvCount / cnv.sampleSize.toFloat()
       }.max()?:0.0f
   
       if(verbose)
           System.err.println "Found ${dgvCnvs.size()} plausible known DGV variants for ${v}, and ${spanning.size()} spanning CNVs (max freq = $spanningFreq)" 
		   
	   return [ spanning: spanning, spanningFreq: spanningFreq, ]
   }
   
   List<Region> filterByType(String type, List<Region> regions) {
       if(type == "LOSS")
          return regions.grep { it.varType in ["Loss","Gain+Loss","loss","gain+loss","deletion"] }
       else
          return regions.grep { it.varType in ["Gain","Gain+Loss","gain","gain+loss","duplication"] }
   }
   
   List<GRange> findCompatibleRanges(IRegion v) {
       
       // First determine the true possible range of the CNV
       // That means, from the first upstream unaffected target region 
       // through to the first downstream unaffected target region
       Region maxRange = computeMaxRange(v)
           
       // For now we consider the region in the VCF to represet exactly
       // the minimum range ... however we could take into account
       // one day the "soft" boundaries for an imprecise CNV call and put
       // some margins here
       Region minRange = new Region(v.chr, v.range)
           
       // Find in DGV any CNVs that *start* inside the required region
       def compatibleRanges = dgv.getOverlaps(minRange).grep { IntRange r ->
           boolean result = (r.from > maxRange.from) && (r.from < minRange.from) &&
               (r.to > minRange.to) && (r.to < maxRange.to)
           return result
       }
       
       return compatibleRanges
   }
   
   Region computeMaxRange(IRegion v) {
       // Need the first upstream target region
       IntRange prevRange = target.previousRange(v.chr, v.range.from)
       IntRange nextRange = target.nextRange(v.chr, v.range.to)
       
       if(prevRange == null)
           prevRange = 1..1
           
       if(nextRange == null)
           nextRange = Integer.MAX_VALUE..Integer.MAX_VALUE
       
       return new Region(v.chr, (prevRange.to+1)..nextRange.from)
   }
    
    public static void main(String [] args) {
        
        Cli cli = new Cli(header: "CNV Annotator for Targeted Sequencing")
        cli.with {
            vcf "VCF to annotate", args:1, required:true
            bed "BED file of regions sequenced", args:1, required:true
            dgv "DGV file from UCSC (dgvMerged)", args:1, required:true
//            omim "OMIM genes file", args: 1, required:true
//            o "Output file", args:1, required:true
        }
        
        def opts = cli.parse(args)
        if(!opts)
            System.exit(1)
            
        BED targetRegions = Utils.time("Reading target regions ...") { new BED(opts.bed).load() }
        new TargetedCNVAnnotator(targetRegions, opts.dgv).annotate(opts.vcf, null)
    }

}
