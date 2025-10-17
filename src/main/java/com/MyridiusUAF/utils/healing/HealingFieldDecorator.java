package com.MyridiusUAF.utils.healing;

import com.MyridiusUAF.ai.LlmClient;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.pagefactory.DefaultElementLocatorFactory;
import org.openqa.selenium.support.pagefactory.DefaultFieldDecorator;
import org.openqa.selenium.support.pagefactory.ElementLocator;

import java.util.List;

public class HealingFieldDecorator extends DefaultFieldDecorator {

    private final WebDriver driver;
    private final LlmClient llm;

    public HealingFieldDecorator(WebDriver driver, LlmClient llm) {
        super(new DefaultElementLocatorFactory(driver));
        this.driver = driver;
        this.llm = llm;
    }

    @Override
    protected WebElement proxyForLocator(ClassLoader loader, ElementLocator locator) {
        // delegate Selenium’s proxy creation, but route calls through our healing locator
        return super.proxyForLocator(loader, new HealingElementLocator(driver, locator, llm));
    }

    @Override
    protected List<WebElement> proxyForListLocator(ClassLoader loader, ElementLocator locator) {
        return super.proxyForListLocator(loader, new HealingElementLocator(driver, locator, llm));
    }
}
