*** Settings ***
Documentation       Robot to solve the first challenge at rpachallenge.com,
...                 which consists of filling a form that randomly rearranges
...                 itself for ten times, with data taken from a provided
...                 Microsoft Excel file. Return Congratulation message to Camunda.

Library             Camunda.Browser.Selenium
Library             Camunda.Excel.Files
Library             Camunda.HTTP
Library             Camunda

*** Tasks ***
Complete the challenge
    Start the challenge
    Fill the forms
    Collect the results

*** Keywords ***
Start the challenge
    Open Browser   http://rpachallenge.com/    options=add_argument("--headless");
    Camunda.HTTP.Download
    ...    http://rpachallenge.com/assets/downloadFiles/challenge.xlsx
    ...    overwrite=True
    Click Button    xpath=//button[contains(text(), 'Start')]

Fill the forms
    ${people}=    Get the list of people from the Excel file
    FOR    ${person}    IN    @{people}
        Fill and submit the form    ${person}
    END

Get the list of people from the Excel file
    Open Workbook    challenge.xlsx
    ${table}=    Read Worksheet As Table    header=True
    Close Workbook
    RETURN    ${table}

Fill and submit the form
    [Arguments]    ${person}
    Input Text    xpath=//input[@ng-reflect-name="labelFirstName"]    ${person}[First Name]
    Input Text    xpath=//input[@ng-reflect-name="labelLastName"]    ${person}[Last Name]
    Input Text    xpath=//input[@ng-reflect-name="labelCompanyName"]    ${person}[Company Name]
    Input Text    xpath=//input[@ng-reflect-name="labelRole"]    ${person}[Role in Company]
    Input Text    xpath=//input[@ng-reflect-name="labelAddress"]    ${person}[Address]
    Input Text    xpath=//input[@ng-reflect-name="labelEmail"]    ${person}[Email]
    Input Text    xpath=//input[@ng-reflect-name="labelPhone"]    ${person}[Phone Number]
    Click Button    xpath=//input[@type='submit']

Collect the results
    ${resultText}=    Get Text    xpath=//div[contains(@class, 'congratulations')]//div[contains(@class, 'message2')]
    Set Output Variable    resultText    ${resultText}
    Close Browser