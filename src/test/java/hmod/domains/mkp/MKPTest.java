package hmod.domains.mkp;


import static hmod.core.FlowchartFactory.run;
import java.io.IOException;
import optefx.loader.Module;
import optefx.loader.ModuleLoader;
import optefx.util.output.OutputConfig;
import optefx.util.output.OutputManager;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Enrique Urra C.
 */
public class MKPTest
{
    @BeforeClass
    public static void init()
    {
        OutputManager.getCurrent().setOutputsFromConfig(new OutputConfig().
                addSystemOutputId(MKPOutputIds.NEW_SOLUTION_INFO).
                addSystemOutputId(MKPOutputIds.FINAL_SOLUTION_INFO)
        ); 
    }
    
    @Test
    public void doTest() throws IOException
    {
        Module mod = new ModuleLoader().
            load(MKPDomain.class).
            setParameter(MKPDomain.FILL_METHOD, MKPDomain.GREEDY_FILL).
            setParameter(MKPDomain.INSTANCE, "../launcher-testing/input/problems/mkp/mknapcb9.txt:29").
            setParameter(MKPDomain.LP_OPTIMUM_SET, "../launcher-testing/input/problems/mkp/mknapcb-lp-opt.txt").
            getModule();
        
        MKPDomain mkpDomain = mod.getInstance(MKPDomain.class);
        
        run(
            mkpDomain.initSolution(),
            mkpDomain.saveSolution(),

            mkpDomain.heuristic(MKPDomain.ADD_RANDOM),
            mkpDomain.saveSolution(),

            mkpDomain.heuristic(MKPDomain.REMOVE_RANDOM),
            mkpDomain.saveSolution(),

            mkpDomain.heuristic(MKPDomain.MULTI_REMOVE),
            mkpDomain.saveSolution(),

            mkpDomain.heuristic(MKPDomain.MULTI_REMOVE_AND_RANDOM_FILL),
            mkpDomain.saveSolution(),

            mkpDomain.heuristic(MKPDomain.MULTI_REMOVE_AND_GREEDY_FILL),
            mkpDomain.saveSolution()
        );
    }
}
