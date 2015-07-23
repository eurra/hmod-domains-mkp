
package hmod.domains.mkp;

import static hmod.core.FlowchartFactory.*;
import hmod.core.PlaceholderStatement;
import hmod.core.Statement;
import hmod.solvers.common.MutableIterationHandler;
import optefx.loader.ComponentRegister;
import optefx.loader.LoadsComponent;
import optefx.loader.ModuleLoadException;
import optefx.loader.Parameter;
import optefx.loader.ParameterRegister;
import optefx.loader.SelectableValue;
import optefx.loader.Selector;

/**
 *
 * @author Enrique Urra C.
 */
public final class MKPDomain
{    
    public static final class DefaultFillMethod extends SelectableValue<Statement> implements MKPFillMethod
    {
        private DefaultFillMethod()
        {
            super(MKPDomain.class, (d) -> d.fillMethods);
        }
    }
    
    public static final class DefaultHeuristic extends SelectableValue<Statement> implements MKPHeuristic
    {
        private DefaultHeuristic()
        {
            super(MKPDomain.class, (d) -> d.heuristics);
        }
    }
    
    public static final DefaultFillMethod RANDOM_FILL = new DefaultFillMethod();
    public static final DefaultFillMethod GREEDY_FILL = new DefaultFillMethod();
    
    public static final DefaultHeuristic ADD_RANDOM = new DefaultHeuristic();
    public static final DefaultHeuristic ADD_GREEDY = new DefaultHeuristic();
    public static final DefaultHeuristic REMOVE_RANDOM = new DefaultHeuristic();
    public static final DefaultHeuristic REMOVE_GREEDY = new DefaultHeuristic();
    public static final DefaultHeuristic MULTI_REMOVE = new DefaultHeuristic();
    public static final DefaultHeuristic MULTI_REMOVE_AND_RANDOM_FILL = new DefaultHeuristic();;
    public static final DefaultHeuristic MULTI_REMOVE_AND_GREEDY_FILL = new DefaultHeuristic();
    
    public static final Parameter<MKPFillMethod> FILL_METHOD = new Parameter<>("MKPDomain.FILL_METHOD");  
    public static final Parameter<String> INSTANCE = new Parameter<>("MKPDomain.INSTANCE_FILE");
    public static final Parameter<String> LP_OPTIMUM_SET = new Parameter<>("MKPDomain.LP_OPTIMUM_SET");
    
    @LoadsComponent({ MKPDomain.class, ProblemInstanceHandler.class, SolutionHandler.class, SolutionBuilderHandler.class })
    public static void load(ComponentRegister cr, ParameterRegister pr) throws ModuleLoadException
    {
        MKPFillMethod fm = pr.getRequiredValue(FILL_METHOD);
        String[] instanceFileInfo = pr.getRequiredValue(INSTANCE).split(":");
        String instanceFile = instanceFileInfo[0];
        int instanceNumber = Integer.parseInt(instanceFileInfo[1]);
        String lpOptimumsFile = pr.getValue(LP_OPTIMUM_SET);
        
        ProblemInstanceHandler pih = cr.provide(new ProblemInstanceHandler(instanceFile, instanceNumber, lpOptimumsFile));
        MutableSolutionHandler sh = cr.provide(new MutableSolutionHandler(), SolutionHandler.class);
        SolutionBuilderHandler sbh = cr.provide(new SolutionBuilderHandler(pih));
        MKPDomain mkpDomain = cr.provide(new MKPDomain(pih, sh, sbh));
        
        pr.addBoundHandler(fm, (v) -> mkpDomain.fillMethod.set(v));
    }
    
    private Statement initSolution;
    private Statement loadSolution;
    private Statement saveSolution;
    private Statement reportSolution;
    private final Selector<MKPFillMethod, Statement> fillMethods = new Selector<>();
    private final Selector<MKPHeuristic, Statement> heuristics = new Selector<>();
    private final PlaceholderStatement<Statement> fillMethod = new PlaceholderStatement<>();

    public Statement initSolution() { return initSolution; }
    public Statement loadSolution() { return loadSolution; }
    public Statement saveSolution() { return saveSolution; }
    public Statement reportSolution() { return reportSolution; }
    public Statement fillMethod(DefaultFillMethod fm) { return fillMethods.get(fm); }
    public Statement heuristic(DefaultHeuristic h) { return heuristics.get(h); }

    private MKPDomain(ProblemInstanceHandler pih,
                      MutableSolutionHandler sh,
                      SolutionBuilderHandler sbh)
    {
        MKPOperators mkpOps = new MKPOperators(sh, sbh);
        
        Statement randomFill = fillMethods.add(RANDOM_FILL, block(() -> {
            ItemListHandler itemListHandler = new ItemListHandler(pih);
            SelectedItemHandler selectedItemHandler = new SelectedItemHandler(pih);

            return mkpOps.fillMethod(
                itemListHandler, 
                selectedItemHandler, 
                MKPOperators.selectRandomItemInList(itemListHandler, selectedItemHandler)
            );
        }));
        
        Statement greedyFill = fillMethods.add(GREEDY_FILL, block(() -> {
            ItemListHandler itemListHandler = new ItemListHandler(pih);
            SelectedItemHandler selectedItemHandler = new SelectedItemHandler(pih);

            return mkpOps.fillMethod(
                itemListHandler, 
                selectedItemHandler, 
                MKPOperators.selectMostProfitableItemInList(itemListHandler, selectedItemHandler)
            );
        }));
        
        initSolution = block(
            If(NOT(sh::isSolutionProvided)).then(
                sbh::clear,
                fillMethod
            )
        );
        
        loadSolution = block(
            If(sh::isSolutionProvided).then(
                mkpOps::loadProvidedSolutionInBuilder
            )
        );
        
        saveSolution = mkpOps::saveBuildedSolutionForRetrieving;
        reportSolution = mkpOps::reportSolution;
        
        Statement removeRandom = heuristics.add(REMOVE_RANDOM, block(() -> {
            SelectedItemHandler sih = new SelectedItemHandler(pih);

            return block(
                sih::clear,
                mkpOps.selectRandomIncludedItemInBuild(sih),
                mkpOps.removeSelectedItemFromBuild(sih)
            );
        }));
        
        heuristics.add(REMOVE_GREEDY, block(() -> {
            SelectedItemHandler sih = new SelectedItemHandler(pih);
            ItemListHandler ilh = new ItemListHandler(pih);

            return block(
                sih::clear, ilh::clear,
                mkpOps.storeCurrentItemsInList(ilh),
                MKPOperators.selectWorstProfitableItemInList(ilh, sih),
                mkpOps.removeSelectedItemFromBuild(sih)
            );
        }));
        
        Statement multiRemove = heuristics.add(MULTI_REMOVE, block(() -> {
            MutableIterationHandler iterationHandler = new MutableIterationHandler();

            return block(
                mkpOps.initRandomIteratorFromCurrentItems(iterationHandler),
                While(NOT(iterationHandler::areIterationsFinished)).Do(
                    removeRandom,
                    iterationHandler::advanceIteration
                )
            );
        }));
        
        heuristics.add(ADD_RANDOM, block(() -> {
            SelectedItemHandler sih = new SelectedItemHandler(pih);

            return block(
                If(NOT(sbh::checkIfCanAdd)).then(
                    heuristics.get(REMOVE_RANDOM)
                ),
                sih::clear,                
                mkpOps.selectRandomAvailableItemInBuild(sih),
                mkpOps.addSelectedItemToBuild(sih)
            );
        }));
        
        heuristics.add(ADD_GREEDY, block(() -> {
            SelectedItemHandler sih = new SelectedItemHandler(pih);
            ItemListHandler ilh = new ItemListHandler(pih);

            return block(
                If(sbh::checkIfCanRemove).then(
                    heuristics.get(REMOVE_RANDOM)
                ),
                sih::clear, ilh::clear,
                mkpOps.storeAvailableItemsInList(ilh),
                MKPOperators.selectMostProfitableItemInList(ilh, sih),
                mkpOps.addSelectedItemToBuild(sih)
            );
        }));
        
        heuristics.add(MULTI_REMOVE_AND_RANDOM_FILL, block(
            multiRemove,
            randomFill
        ));
        
        heuristics.add(MULTI_REMOVE_AND_GREEDY_FILL, block(
            multiRemove,
            greedyFill
        ));
    }
}
