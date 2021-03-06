# Groovy NGS Utils

A collection of utilities for working with next generation (MPS) sequencing data in Groovy

This is a collection of Groovy wrappers and interfaces that make it easy to perform 
scripting operations with Groovy to process NGS data.

The kind of operations currently supported are:

  * Reading, processing and filtering VCF files, including integration with common annotation sources such as VEP, Annovar and SnpEFF
  * Reading, processing and filtering BED files or any source of genomic ranges
  * Reading, processing and performing logical operations with pedigree (PED) files and family structures
  * Working with SAM files (particularly, generating and working with Pileups)
  * Predicting Restriction Enzyme cut sites
  * A range of statistical operations including R-like data frames and linear modeling constructs

Since these utilities are optimized for scripting, they live in the default Java package. This means you can 
easily write command line scripts, such as:

```bash
  # Get me a filtered VCF with QUAL>20 and DP > 5
  cat my.vcf | groovy -e 'VCF.filter { it.qual > 20 && it.info.DP.toInteger()>5 }' > filtered.vcf

  # Which read shave MAPQ = 0?
  cat my.bam | groovy -e 'SAM.eachRead { if(it.mappingQuality == 0) { println it.readName } }'
  
  # What's the median coverage of bedtools output?
  coverageBed -d  -abam test.bam -b test.bed | cut -f 6 | groovy -e 'Stats.read().median'

  # What are the bases piled up at 25870138?
  groovy -e 'println(new SAM("test.bam").basesAt("chr1", 25870138))'

  # What are the exons in the DVL1 gene?
  groovy -e 'println(RefGenes.download().getExons("DVL1").join(", "))'

  # What are the total number of bases overlapped by regions in my BED file?
  groovy -e 'println(new BED("./tests/data/small.overlaps.bed").load().reduce().size())'

```

These are only examples and barely scratch the surface of all the functions built into
groovy-ngs-utils. You can find documentation about the individual classes and methods
in the [API Documentation](http://ssadedin.github.io/groovy-ngs-utils/doc/index.html)
  
Everything is built upon Samtools, Picard Tools, BioJava and Apache commons-math. The jar file that 
is built bundles all the necessary libraries so that you can easily include them all with just one
classpath entry (or put it into your .groovy/lib).

Careful attention has been paid to make, wherever possible, operations operate on streaming data so that
memory is not a bottleneck in manipulating large data sets.

## Building

Clone the repository:

```
    git clone git@github.com:ssadedin/groovy-ngs-utils.git
    git submodule update --init
```

Run gradle:

```
    cd groovy-ngs-utils
    ./gradlew clean jar
```

Note: if behind a proxy, you can specify it like so:

```
./gradlew -Dhttp.proxyHost=<host> -Dhttp.proxyPort=<port> clean jar
```

## Installation

If you want access to the classes from your own scripts easily, put the jar in your ~/.groovy/lib folder:

```bash
    mkdir -p ~/.groovy/lib
    cp build/libs/groovy-ngs-utils.jar ~/.groovy/lib
```

This way you can use commands without modifying your classpath at all.

However: since this places groovy-ngs-utils.jar into your default groovy path
it is probably not a great idea since it may introduce versions of classes to your default Groovy
classpath that conflict with classes other applications using Groovy depend on.
It's an easy way to experiment with the library but you are better off
specifying it explicitly when you need it:

So there are some other ways to use it.

First, to execute ad hoc scripts, you can use the `gngs` tool in the bin directory:

```bash
# What type of variants are in my VCF?
./bin/gngs 'println(VCF.parse("some.vcf").countBy { it.type })'
```

You can get a GNGS enabled interactive Groovy Shell like this:

```
./bin/gngsh
groovy:000> new SAM("my.bam").basesAt("chr7", 117292917)
===> [A:5, total:5, C:0, T:0, D:0, G:0]
```

For the command line tools implemented by GNGS, you can run them using `gngstools`:

```
./bin/gngstool ExtractFASTQ -bam my.bam | bwa mem -p hg19.fasta - | samtools view -Sb - >  my.realigned.bam
```

