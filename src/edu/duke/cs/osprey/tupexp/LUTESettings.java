/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.tupexp;

import edu.duke.cs.osprey.control.ParamSet;

/**
 *
 * Settings for LUTE
 * 
 * @author mhall44
 */
public class LUTESettings {
    
    boolean useLUTE=false;
    public double goalResid=0.01;
    boolean useRelWt=false;
    boolean useThreshWt=false;
    
    public LUTESettings(){
        //by default, no LUTE
        useLUTE = false;
    }
    
    public LUTESettings(ParamSet params){
        //initialize from input parameter set
        useLUTE = params.getBool("USETUPEXP");
        goalResid = params.getDouble("LUTEGOALRESID");
        useRelWt = params.getBool("LUTERELWT");
        useRelWt = params.getBool("LUTETHRESHWT");
    }
}
