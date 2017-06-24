/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.agarwalamit;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import opdytsintegration.MATSimSimulator2;
import opdytsintegration.MATSimStateFactoryImpl;
import opdytsintegration.utils.OpdytsConfigGroup;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.testcases.MatsimTestUtils;
import playground.agarwalamit.opdyts.*;
import playground.agarwalamit.opdyts.analysis.OpdytsModalStatsControlerListener;
import playground.agarwalamit.opdyts.equil.EquilDistanceDistribution;
import playground.agarwalamit.utils.FileUtils;
import playground.kai.usecases.opdytsintegration.modechoice.EveryIterationScoringParameters;

/**
 * Since, only network modes correctly identified, only car, bike modes are tested.
 *
 * Created by amit on 02.05.17.
 */


public class EquilOpdytsTest {

    private static double randomVariance = 1.0;
    private static int iterationsToConvergence = 30;

    @Rule
    public MatsimTestUtils helper = new MatsimTestUtils();

    private static String EQUIL_DIR = "../../examples/scenarios/equil-mixedTraffic/";
    private static final OpdytsScenario EQUIL_MIXEDTRAFFIC = OpdytsScenario.EQUIL_MIXEDTRAFFIC;

    private static final boolean isPlansRelaxed = true;

    @Test@Ignore
    public void runTest(){
        List<String> modes2consider = Arrays.asList("car","bicycle");

        String outDir = helper.getOutputDirectory();
        Config config = setUpAndReturnConfig(modes2consider);

        //==
        if(! isPlansRelaxed) {
            relaxPlansAndUpdateConfig(config, outDir, modes2consider);
        }

        Scenario scenario = ScenarioUtils.loadScenario(config);
        scenario.getConfig().controler().setOutputDirectory(outDir);
        runOpdyts(modes2consider,scenario,outDir);
    }

    private void runOpdyts(final List<String> modes2consider, final Scenario scenario, final String outDir){

        MATSimOpdytsIntegrationRunner runner = new MATSimOpdytsIntegrationRunner(scenario);

        DistanceDistribution distanceDistribution = new EquilDistanceDistribution(EQUIL_MIXEDTRAFFIC);
        OpdytsModalStatsControlerListener stasControlerListner = new OpdytsModalStatsControlerListener(modes2consider,distanceDistribution);

        MATSimSimulator2<ModeChoiceDecisionVariable> simulator = runner.newMATSimSimulator(new MATSimStateFactoryImpl<>());
        simulator.addOverridingModule(new AbstractModule() {

            @Override
            public void install() {
                addControlerListenerBinding().toInstance(stasControlerListner);
                bind(ScoringParametersForPerson.class).to(EveryIterationScoringParameters.class);
            }
        });

        runner.run(
                new ModeChoiceRandomizer(scenario, RandomizedUtilityParametersChoser.ONLY_ASC,   EQUIL_MIXEDTRAFFIC, null, modes2consider),
                new ModeChoiceDecisionVariable(scenario.getConfig().planCalcScore(),scenario, modes2consider, EQUIL_MIXEDTRAFFIC),
                new ModeChoiceObjectiveFunction(distanceDistribution)
        );

    }

    private void relaxPlansAndUpdateConfig(final Config config, final String outDir, final List<String> modes2consider){

        config.controler().setOutputDirectory(outDir+"/relaxingPlans/");
        config.controler().setLastIteration(20);
        config.strategy().setFractionOfIterationsToDisableInnovation(0.8);

        Scenario scenarioPlansRelaxor = ScenarioUtils.loadScenario(config);
        // following is taken from KNBerlinControler.prepareScenario(...);
        // modify equil plans:
        double time = 6*3600. ;
        for ( Person person : scenarioPlansRelaxor.getPopulation().getPersons().values() ) {
            Plan plan = person.getSelectedPlan() ;
            Activity activity = (Activity) plan.getPlanElements().get(0) ;
            activity.setEndTime(time);
            time++ ;
        }

        Controler controler = new Controler(scenarioPlansRelaxor);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addControlerListenerBinding().toInstance(new OpdytsModalStatsControlerListener(modes2consider, new EquilDistanceDistribution(EQUIL_MIXEDTRAFFIC)));
            }
        });
        controler.run();

        FileUtils.deleteIntermediateIterations(outDir,controler.getConfig().controler().getFirstIteration(), controler.getConfig().controler().getLastIteration());

        // set back settings for opdyts
        File file = new File(config.controler().getOutputDirectory()+"/output_plans.xml.gz");
        config.plans().setInputFile(file.getAbsoluteFile().getAbsolutePath());
        config.controler().setOutputDirectory(outDir);
        config.strategy().setFractionOfIterationsToDisableInnovation(Double.POSITIVE_INFINITY);
    }

    private Config setUpAndReturnConfig(final List<String> modes2consider){

        Config config = ConfigUtils.loadConfig(EQUIL_DIR+"/config-with-mode-vehicles.xml", new OpdytsConfigGroup());
        config.plans().setInputFile("plans2000.xml.gz");

        //== default config has limited inputs
        StrategyConfigGroup strategies = config.strategy();
        strategies.clearStrategySettings();

        config.changeMode().setModes( modes2consider.toArray(new String [modes2consider.size()]));
        StrategyConfigGroup.StrategySettings modeChoice = new StrategyConfigGroup.StrategySettings();
        modeChoice.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode.name());
        modeChoice.setWeight(0.1);
        config.strategy().addStrategySettings(modeChoice);

        StrategyConfigGroup.StrategySettings expChangeBeta = new StrategyConfigGroup.StrategySettings();
        expChangeBeta.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta);
        expChangeBeta.setWeight(0.9);
        config.strategy().addStrategySettings(expChangeBeta);
        //==

        //== planCalcScore params (initialize will all defaults).
        for ( PlanCalcScoreConfigGroup.ActivityParams params : config.planCalcScore().getActivityParams() ) {
            params.setTypicalDurationScoreComputation( PlanCalcScoreConfigGroup.TypicalDurationScoreComputation.relative );
        }

        // remove other mode params
        PlanCalcScoreConfigGroup planCalcScoreConfigGroup = config.planCalcScore();
        for ( PlanCalcScoreConfigGroup.ModeParams params : planCalcScoreConfigGroup.getModes().values() ) {
            planCalcScoreConfigGroup.removeParameterSet(params);
        }

        PlanCalcScoreConfigGroup.ModeParams mpCar = new PlanCalcScoreConfigGroup.ModeParams("car");
        PlanCalcScoreConfigGroup.ModeParams mpBike = new PlanCalcScoreConfigGroup.ModeParams("bicycle");
        mpBike.setMarginalUtilityOfTraveling(0.);


        planCalcScoreConfigGroup.addModeParams(mpCar);
        planCalcScoreConfigGroup.addModeParams(mpBike);
        //==

        //==
        config.qsim().setTrafficDynamics( QSimConfigGroup.TrafficDynamics.withHoles );
        config.qsim().setUsingFastCapacityUpdate(true);

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        return config;
    }


}