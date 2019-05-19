package gngs.plot

import static org.junit.Assert.*

import org.junit.Test

class PlotTest {

//    @Test
    public void testLines() {
        Plot p = new Plot(
            title:'Simons great plot',
            xLabel: "Random Stuff You Don't Care About",
            yLabel: "Stuff You Do Care About",
            ) << \
            new Lines(x: [1,2,3,4,5,6,7], y: [1,3,6,8,6,5,2], name: 'Bananas') << \
            new Lines(x: [1,2,3,4,5,6,7], y: [1,5,9,18,4,2,1], name: 'Oranges')
        p.save('test.png')
    }
    
//    @Test
    public void testBars() {
        Plot p = new Plot(title:'Simons great plot') << \
            new Bars(x: [1,2,3,4,5,6,7], y: [1,3,6,8,6,5,2], name: 'Bananas') << \
            new Bars(x: [1,2,3,4,5,6,7], y: [1,5,9,18,4,2,1], name: 'Oranges')
        p.save('testbars.png')        
    }
    
    
    
//    @Test
    void 'test round up oom' () {
        
        def p = new Plot()
        
        assert PlotUtils.roundUpToOOM(7d) == 10d
        
        assert PlotUtils.roundUpToOOM(17d) == 20d
        
    }
    

    @Test
    void testHistogram() {
        Random r = new Random()
//        List<Double> data = (1..1000).collect { r.nextGaussian() }
        
        Map dataMap = [
            (-3) : 3,
            (-2) : 5,
            (-1) : 10,
            0 : 20,
            1 : 10, 
            2 : 5,
            3 : 3,
            4 : 1
        ]
        
        List data = dataMap.collect {
            [it.key] * it.value
        }.sum()
        
        println "Data is: " + data
        
        Histogram hist = 
            new Histogram(
                data:data, 
                binCount:8, 
                title:'Foos are really frogjible',
                xLabel: 'Frobbles of the Fribfrob',
                yLabel: 'Fringles of the funglefib')
        hist.save('testhist.png')
    }
}
