package io.galeb;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(
        monochrome = true,
        plugin= {"pretty"},
        glue = {"io.galeb"},
        features= {"classpath:"}
        )
public class CucumberTest {

}
