
// методы для jenkins

def setScript(final script){
    MainBuild.s_script = script
    MainBuild.s_libScript = this
}

def addTest(final String name, final String node = ''){
    MainBuild.addTest(name, node)
}

def startBuild(){
    MainBuild.startBuild()
}

def setNode(final String node){
    MainBuild.s_node = node
}

def setPathToSource(final repo, final branch){
    MainBuild.s_repo = repo
    MainBuild.s_branch = branch
    MainBuild.s_multibranch = false
}

def setRunModeOrdinaryApplication(){
    MainBuild.s_runModeOrdinaryApplication = true
}

def setPath1C(final path1C){
    MainBuild.s_path1C = path1C
}

def setDebugMode(){
    MainBuild.s_debug = true
    MainBuild.s_sendMsg = false
    disableScanTask()
}

def disableScanTask(){
    MainBuild.s_scanTask = false
}

def setArtifactsPath(final String path){
    MainBuild.s_artifactsPath = path
}

def useChangeObjects(){
    MainBuild.s_useChangeObjects = true
}

// ядро

final class MainBuild{

    public static s_script
    public static s_libScript
    public static String s_node = ''
    public static String s_repo = ''
    public static String s_branch = ''
    public static String s_path1C = ''
    public static String s_artifactsPath = ''
    public static String s_repo_from_scm = ''
    public static String s_sonar_host_url = ''
    public static Boolean s_multibranch = true
    public static Boolean s_debug = false
    public static Boolean s_sendMsg = true
    public static Boolean s_runModeOrdinaryApplication = false
    public static Boolean s_scanTask = true
    public static Boolean s_useChangeObjects = false
    private static String s_baseFolder = 'base'
    private static String s_fileСhangeObject = 'Object.txt'
    private static ArrayList s_tests = new ArrayList()
    private static ArrayList s_testName = new ArrayList()
    private static ArrayList s_existResources = new ArrayList()

    static void addTest(final String name, final String node){
        s_testName.add(['name' : name, 'node': node])
    }

    static void startBuild(){

        final String stashName = 'artifacts'
        sendMsg(true)

        TestCase.s_script = s_script
        s_script.currentBuild.result = "SUCCESS"

        s_script.node(s_node) {

            try {

                s_script.timestamps {

                    s_script.deleteDir()

                    getSource()
                    if (s_scanTask) {
                        getTask()
                    }
                    createBase()
                    getTestFromName()
                    getResourcesForTest()

                    if (s_artifactsPath != '') {
                        stashResource(stashName, getArtifactsPath())
                    }

                }

            } catch (exception) {
                setResultAfterError(exception)
            } finally {
                if (!s_debug) {
                   deleteWorkspace()
               }
            }

        }
        try {

            if (s_script.currentBuild.result == "SUCCESS"){
                s_script.stage('RunTests') {
                    s_libScript.runTests(s_tests)
                }
                s_script.node('built-in') {
                    s_script.stage('GetResult') {
                        getResult()
                    }
                }

            }

        } finally {
            archiveArtifacts(stashName)
            sendMsg(false)
        }

    }

    static void stashResource(final String name, final String path){

        if (!resourceExist(name)){

            debug('stash: ' + name, 32)
            debug('path: ' + path, 32)

            s_script.stash(includes: path, name: name)
            s_existResources.add(name)

        }else{
            debug('exist stash: ' + name, 32)
        }

    }

    static void unstashResource(final String name){

        debug('unstash: ' + name, 32)
        s_script.unstash(name)

    }

    static Boolean resourceExist(final String name){
        return s_existResources.contains(name)
    }

    static void debug(final String text, final Integer color = 34){

        if (s_debug){
            echoColourText(text, color)
        }

    }

    static void echoColourText(final String text, final Integer color){
        s_script.ansiColor('xterm') {
            s_script.echo('\u001B[' + color.toString() + 'm' + text + '\u001B[0m')
        }
    }

    static void startBat(final String text){

        debug('startBat: ' + text)
        s_script.bat(text)

    }

    static void startBatСyrillic(final String text){

        debug('startBatСyrillic: ' + text)
        s_script.bat('''chcp 65001 >NUL
                        set x=''' + text + '''
                        chcp 866 >NUL
                        %x%''')

    }

    static void run1C(final String command, final String base = '', final String log = '', final boolean notUsePath=false){
        
        if (s_debug) {
            startBat('runner1c version')
        }

        ArrayList partOfText = new ArrayList()
        partOfText.add('runner1c')
        
        if (s_debug) {
            partOfText.add('--debug')
        }
        
        partOfText.add(command)

        if (!notUsePath && s_path1C != ''){
            partOfText.add(String.format('--path "%1$s"', s_path1C))
        }

        if (log != ''){
            partOfText.add(String.format('--log "%1$s"', log))
        }

        if (base != ""){
            partOfText.add(String.format('--connection "File=%1$s"', base))
        }
        
        s_script.timeout(90) {
            startBat(partOfText.join(' '))
        }

    }

    static String getTextFromFile(final String pathToFile){
        return s_script.readFile(pathToFile)
    }

    static void writeTextToFile(final String pathToFile, final String text){
        s_script.writeFile(encoding: 'UTF-8', file: pathToFile, text: text)
    }

    static Boolean getRunModeOrdinaryApplication(){
        return s_runModeOrdinaryApplication
    }

    static String getPath1C(){
        return s_path1C
    }

    static String baseFolder(){
        return s_baseFolder
    }

    static String fileChangeObject(){
        return s_fileСhangeObject
    }

    static void deleteWorkspace(){

        s_script.sleep(10)
        s_script.cleanWs(
                cleanWhenAborted: false,
                cleanWhenFailure: false,
                cleanWhenNotBuilt: false
        )

    }

    static void setResultAfterError(final exception){

        showError(exception)
        s_script.currentBuild.result = "FAILURE"

    }

    static void setResultHTML(final String testName, final String pathToFile){
        findErrorInFile(testName, pathToFile)
        publishResultHTML(testName, pathToFile)
    }

    static void publishResultHTML(final String testName, final String pathToFile){

        final String pathToResult = 'Result'

        startBat('mkdir ' + pathToResult)
        startBat(String.format('copy %1$s %2$s', pathToFile, pathToResult))

        s_script.publishHTML(
                target:[
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: pathToResult,
                        reportFiles: pathToFile,
                        reportName: testName
                ]
        )

        startBat('rmdir /s /q ' + pathToResult)

    }

    static void setResultAllure(final String testName, final String pathToFolder){

        final def files = s_script.findFiles(glob: pathToFolder + '//*.xml')
        for(int count = 0; count < files.size(); count++) {
            if (findErrorInFile(testName, files[count].path, true)){
                break
            }
        }

    }

    static String getResultFolder(){
        return 'resultFolder'
    }

    static String getResultFromFile(final String fileName){
        return getTextFromFile(fileName)
    }

    static void setUnstableResult(final String testName){

        if (s_script.currentBuild.result=='SUCCESS'){
            s_script.currentBuild.result = 'UNSTABLE'
        }

        s_script.currentBuild.description = addTextToString(s_script.currentBuild.description, testName)

    }

    static Boolean getUseChangeObjects(){
        return s_useChangeObjects
    }

    static String getBranch(){
    
        String branch = ''

        if (s_multibranch){
            branch = s_script.env.BRANCH_NAME
        }else{
            branch = s_branch
        }

        return branch

    } 

    static String getRepo(){
    
        String repo = ''

        if (s_multibranch){
            repo = s_repo_from_scm
        }else{
            repo = s_repo
        }

        return repo

    } 

    private static void sortTestsByNode(){

        ArrayList newTests = new ArrayList()
        for(TestCase object: s_tests) {
            if (object.getNode()!=''){
                newTests.add(object)
            }
        }

        newTests += s_tests
        newTests.unique()

        s_tests = newTests

    }

    private static void getSource(){

        s_script.stage('Checkout'){

            if (s_multibranch){

                s_script.properties([
                        s_script.disableConcurrentBuilds(),
                        s_script.buildDiscarder(
                                s_script.logRotator(
                                        artifactDaysToKeepStr: '',
                                        artifactNumToKeepStr: '',
                                        daysToKeepStr: '',
                                        numToKeepStr: '10'
                                )
                        ),
                        s_script.copyArtifactPermission('*')
                ]
                )

                def scmVars = s_script.checkout(s_script.scm)
                s_repo_from_scm = scmVars.GIT_URL.tokenize('/.')[-2]

            }else{
                s_script.git(branch: s_branch, 
                             credentialsId: '87e46017-dc29-49da-b21b-30f47184962d',
                             url: String.format('https://bitbucket.org/%1$s.git', s_repo))
            }

        }

    }

    private static void createBase(){

        s_script.stage('CreateBase'){

            final String workPath = s_script.pwd()
            ArrayList partOfText = new ArrayList()
            partOfText.add(String.format('base_for_test --folder "%1$s" --create_epf --create_cfe', workPath))
            if (s_runModeOrdinaryApplication) {
                partOfText.add('--thick')
            }
            
            run1C(partOfText.join(' '), baseFolder())

            String filePath = baseFolder() + '/1Cv8.1CD'
            saveChangeFiles()

            if (s_script.fileExists(fileChangeObject())) {
                filePath += ', ' + fileChangeObject()
            }

            stashResource(baseFolder(), filePath)
            
        }

    }

    private static void getTask(){

        s_script.recordIssues(
            blameDisabled: true,
            ignoreQualityGate: true,
            sourceCodeEncoding: 'UTF-8',
            tools: [s_script.taskScanner(
                        highTags: 'fixme',
                        ignoreCase: true,
                        includePattern: '**/*.bsl',
                        normalTags: 'todo'
                    )]
        )

    }

    private static void getResourcesForTest(){

        s_script.stage('GetResources'){

            for(final TestCase object: s_tests) {
                object.getResources()
            }

        }

    }

    private static void sendMsg(final boolean StartBuild){

        if (s_sendMsg) {

            String color
            ArrayList message = new ArrayList()
            message.add(String.format('%1$s - #%2$s', s_script.env.JOB_NAME.replace('%2F', '/'), s_script.env.BUILD_NUMBER))
            String grey = '#948f8f'

            if (StartBuild){
                color = grey
                message.add('Started')
            }else{
                String result = s_script.currentBuild.result.substring(0,1) + s_script.currentBuild.result.substring(1).toLowerCase()
                message.add(String.format('%1$s after %2$s', result, s_script.currentBuild.durationString.replace(' and counting', '')))
                switch(s_script.currentBuild.result) {
                    case "SUCCESS":
                        color = 'good'
                        break
                    case "FAILURE":
                        color = 'danger'
                        break
                    case "UNSTABLE":
                        color = 'warning'
                        break
                    default:
                        color = grey
                }
            }

            message.add(String.format('(<%1$s|%2$s>)', s_script.env.BUILD_URL, 'Open'))
            
            try{
                s_script.timeout(time: 15, unit: 'SECONDS') {
                    s_script.slackSend color: color, failOnError: true, message: message.join(' ')
                }
            }catch (exception){
                showError(exception)
            }


        }

    }

    private static void archiveArtifacts(final String stashName){
        if ((s_artifactsPath != '') && (s_script.currentBuild.result == "SUCCESS")) {
            s_script.node('built-in') {

                unstashResource(stashName)
                s_script.archiveArtifacts(artifacts: getArtifactsPath(), fingerprint: true, onlyIfSuccessful: true)

                if (!s_debug) {
                    deleteWorkspace()
                }

            }
        }
    }

    private static String getArtifactsPath() {
        return s_artifactsPath + ', ' + baseFolder() + '/1Cv8.1CD'
    }

    private static void saveChangeFiles() {
        ArrayList changeFiles = s_libScript.getChangeFiles()
        if (changeFiles.size() > 0) {
            writeTextToFile(s_fileСhangeObject, changeFiles.join(System.lineSeparator()))
        }
    }

    private static void getTestFromName() {

        for(object in s_testName) {
            if (object.name == 'UnitTest') {
                
                Map<String,String> filesWithTags = getTagsInFiles(s_script.findFiles(glob: 'spec/tests/**/*.bsl'))

                for(filesTags in filesWithTags) {

                    ArrayList tags = filesTags.value.split(',')
                   
                    UnitTestType type = UnitTestType.THIN
                    Integer index = tags.indexOf(UnitTestType.THICK.m_name)
                    if (index != -1){
                        type = UnitTestType.THICK
                        tags.remove(index)
                    }
                    
                    String extensions = tags.join(',').replace('Расширение', '')
                    String name = 'Unit' + type.m_name + extensions.replace(',', '')

                    debug('UnitTest name = ' + name)
                    debug('UnitTest type = ' + type.m_name)
                    debug('UnitTest tests = ' + filesTags.key)
                    debug('UnitTest extensions = ' + extensions)

                    s_tests.add(new UnitTest(name, '', type, filesTags.key, extensions))

                }
            
            }else if (object.name == 'PlatformCheck') {
                s_tests.add(new PlatformCheck(object.name + 'Extended', object.node, true))
                s_tests.add(new PlatformCheck(object.name + 'Simple', '', false))
            }else if (object.name == 'CodeAnalysisFull') {
                s_tests.add(new CodeAnalysis(object.name, object.node))
            }else if (object.name == 'BehaveTest') {

                Map<String,String> filesWithTags = getTagsInFiles(s_script.findFiles(glob: 'build/spec/features/*.feature'))

                for(filesTags in filesWithTags) {

                    ArrayList tags = filesTags.value.split(',')
                    
                    BehaveTestType type = BehaveTestType.THIN
                    Integer index = tags.indexOf(BehaveTestType.WEB.m_name)
                    if (index != -1){
                        type = BehaveTestType.WEB
                        tags.remove(index)
                    }

                    String extensions = tags.join(',').replace('Расширение', '')
                    String name = 'Behave' + type.m_name + extensions.replace(',', '')

                    debug('BehaveTest name = ' + name)
                    debug('BehaveTest type = ' + type.m_name)
                    debug('BehaveTest features = ' + filesTags.key)
                    debug('BehaveTest extensions = ' + extensions)

                    s_tests.add(new BehaveTest(name, '', type, filesTags.key, extensions))

                }

            }else if (object.name == 'SonarQube') {
                s_tests.add(new SonarQube(object.name, object.node))
            }
        }
        sortTestsByNode()
    }

    private static void getResult(){

        s_script.deleteDir()

        Boolean BehaveTestExists = false
        Boolean SonarQubeExists = false

        for(TestCase object: s_tests) {
            if (object.getClassName() == 'BehaveTest'){
                unstashResource(object.getStashNameForResult())
                BehaveTestExists = true
            }else if (object.getClassName() == 'SonarQube'){
                SonarQubeExists = true
            }
        }

        if (BehaveTestExists){
            String resultFolder = getResultFolder()
            s_script.cucumber(buildStatus: 'UNSTABLE',
                                failedStepsNumber: 1,
                                fileIncludePattern: '**/*.json',
                                jsonReportDirectory: resultFolder,
                                sortingMethod: 'ALPHABETICAL')
        }

        if (SonarQubeExists){
            
            String link = String.format('<a href="%1$s/dashboard?id=%2$s&branch=%3$s">SonarQube</a>', s_sonar_host_url, MainBuild.getRepo(), MainBuild.getBranch())
            MainBuild.debug('SonarQube = ' + link)
            
            def summary = s_script.createSummary(icon: "/static/8361d0d6/images/16x16/terminal.png")
            summary.appendText(link)

            if (s_script.env.waitForSonar == 'true'){
                s_script.timeout(time: 1, unit: 'HOURS') {
                    def QualityGate = s_script.waitForQualityGate()
                    if (QualityGate.status != 'OK') {
                        MainBuild.setUnstableResult('SonarQube')
                    }  
                }
            }

        }

    }

    private static Boolean findErrorInFile(final String testName, final String pathToFile, final Boolean findFailed = false){

        final String fullPath = s_script.pwd() + File.separator + pathToFile
        final String text = getTextFromFile(fullPath)
        Boolean findError = false

        if (findFailed){
            if (text.indexOf('failed')>0){
                findError = true
            }
        }else{
            if (text!=''){
                findError = true
            }
        }
        if (findError){
            setUnstableResult(testName)
        }

        return findError

    }

    private static String addTextToString(String string, final String text){

        if ((string!='')&&(string!=null)){
            string += ', ' + text
        }else{
            string = text
        }

        return string

    }

    private static void showError(exception) {
        echoColourText('ERROR: ' + exception.getMessage(), 31)
    }

    private static Map<String,String> getTagsInFiles(files){

        ArrayList ignoreTags = ['tree', 'ExportScenarios', 'IgnoreOnCIMainBuild']

        Map<String,String> fileWithTags = new HashMap<String,String>();
        Map<String,String> filesWithTags = new HashMap<String,String>();

        for(int count = 0; count < files.size(); count++) {
            
            def file = files[count] 
            String fileName = file.path
            String text = getTextFromFile(file.path)
            
            ArrayList tags = new ArrayList()
            def matcher = text =~ /@.+/
            while(matcher.find()) {
                String tag = text.substring(matcher.start() + 1, matcher.end())
                if (!(tag in ignoreTags)){
                    tags.add(tag)
                }
            }

            fileWithTags.put(fileName, tags.join(','))
        
        }

        for (tags in fileWithTags.values().unique()){
            
            ArrayList file = new ArrayList()
            for(fileTags in fileWithTags) {
                if (fileTags.value == tags){
                    file.add(fileTags.key)
                }
            }

            filesWithTags.put(file.join(','), tags);
        
        }

        return filesWithTags

    }

}

// не переносить в класс иначе не работает parallel
def runTests(final tests){

    def stepsForParallel = [:]

    for(TestCase object: tests) {
        stepsForParallel[object.getName()] = object.runTest()
    }

    stepsForParallel.failFast = (!MainBuild.s_debug)
    parallel(stepsForParallel)

}

@NonCPS
def getChangeFiles(){

    ArrayList changeFiles = new ArrayList()
    def changeLogSets = currentBuild.changeSets

    for (int i = 0; i < changeLogSets.size(); i++) {

        def entries = changeLogSets[i].items

        for (int j = 0; j < entries.length; j++) {

            def files = entries[j].affectedFiles

            for (int k = 0; k < files.size(); k++) {
                changeFiles.add(files[k].path)
            }

        }

    }

    return changeFiles

}

abstract class TestCase implements Serializable{

    public static s_script
    protected final String m_node
    protected final String m_name
    
    TestCase(final String name, final String node){
        m_node = node
        m_name = name
    }

    def runTest(){
        return {

            s_script.node(m_node) {

                s_script.timestamps {

                    Boolean errorExist = false

                    try {

                        s_script.deleteDir()
                        commandForRunTest()

                    }catch (exception) {
                        errorExist = true
                        MainBuild.setResultAfterError(exception)
                    } finally {

                        commandAfterTest()

                        if (!MainBuild.s_debug) {
                            try {
                                MainBuild.deleteWorkspace()
                            }catch (exception) {
                                errorExist = true
                                MainBuild.setResultAfterError(exception)
                            }
                        }

                    }

                    if (errorExist){
                        s_script.error('error in test')
                    }

                }

            }

        }

    }

    String getName(){
        return m_name
    }

    String getNode(){
        return m_node
    }

    String getClassName(){
        return getClass().getName()
    }

    String getStashNameForResult(){
        return m_name + 'result'
    }

    void getResources(){
        MainBuild.debug(m_name + '_getResources')
    }

    protected void commandForRunTest(){
        MainBuild.debug(m_name + '_commandForRunTest')
    }

    protected void commandAfterTest(){
        MainBuild.debug(m_name + '_commandAfterTest')
    }

    protected void echoEmptyReport(){
        MainBuild.echoColourText(getName() + ' empty report', 35)
    }

}

// описание проверок наследуется от TestCase

class PlatformCheck extends TestCase{

    private final Boolean m_extendedModulesCheck
    private final String m_pathToConfig

    PlatformCheck(final String name, final String node, final Boolean extendedModulesCheck){
        super(name, node)
        m_extendedModulesCheck = extendedModulesCheck
        m_pathToConfig = 'spec\\syntax\\platform'
    }

    void getResources(){
        MainBuild.stashResource(getClassName(), m_pathToConfig + '/*')
    }

    protected void commandForRunTest(){

        MainBuild.unstashResource(MainBuild.baseFolder())
        MainBuild.unstashResource(getClassName())

        final String testName = getName()
        final String resultName = testName + ".html"

        ArrayList partOfText = new ArrayList()
        partOfText.add('platform_check --options')

        if (m_extendedModulesCheck) {
            
            // не убирать пробел в начале иначе не работает запуск в python

            if (MainBuild.getRunModeOrdinaryApplication()) {
                partOfText.add('" -ExtendedModulesCheck"')
            } else {
                partOfText.add('" -ExtendedModulesCheck -CheckUseModality"')
                partOfText.add(String.format('--skip_modality "%1$s\\SkipModality.txt"', m_pathToConfig))
            }

        } else {
            
            partOfText.add('" -ConfigLogIntegrity -IncorrectReferences -ThinClient -Server')
                        
             if (!MainBuild.getRunModeOrdinaryApplication()) {
                partOfText.add('-WebClient')
            }

            partOfText.add('-ExternalConnection -ExternalConnectionServer -ThickClientOrdinaryApplication')
            partOfText.add('-ThickClientServerOrdinaryApplication -UnreferenceProcedures -HandlersExistence')
            partOfText.add('-EmptyHandlers"')
            
        }

        partOfText.add(String.format('--skip_error "%1$s\\IgnoreErrors.txt" --skip_object "%1$s\\IgnoreObjects.txt"', m_pathToConfig))
        MainBuild.run1C(partOfText.join(' '), MainBuild.baseFolder(), resultName)

        if (MainBuild.getTextFromFile(resultName) != '') {
            MainBuild.setUnstableResult(testName)
            MainBuild.publishResultHTML(testName, resultName)
        }else{
            echoEmptyReport()
        }

    }

}

class CodeAnalysis extends TestCase{

    static final String s_command = '--thick --epf SyntaxCheckAcc.epf --options'
    static final String s_baseAcc = 'base_acc'
    static final String s_stashName = 'CodeAnalysis'
    static final String s_pathToConfig = 'spec\\syntax\\acc'
    static final String s_changeObjects = "objects_acc.txt"

    CodeAnalysis(final String name, final String node){
        super(name, node)
    }

    void getResources(){

        if (!MainBuild.resourceExist(s_stashName)) {

            s_script.copyArtifacts(filter: 'base/1Cv8.1CD', fingerprintArtifacts: true, flatten: true, projectName: 'VakulenkoAleksei/acc/master/', target: s_baseAcc)
            s_script.copyArtifacts(filter: 'build/lib/epf/SyntaxCheckAcc.epf', fingerprintArtifacts: true, flatten: true, projectName: 'VakulenkoAleksei/acc/master/')
            
            final String resource = String.format('%1$s/*, %2$s/1Cv8.1CD, SyntaxCheckAcc.epf', s_pathToConfig, s_baseAcc)
            MainBuild.stashResource(s_stashName, resource)

        }

    }

    protected void commandForRunTest(){

        MainBuild.unstashResource(MainBuild.baseFolder())
        MainBuild.unstashResource(s_stashName)

        final String testName = getName()
        final String resultName = testName + ".html"
        final String resultCode = testName + ".txt"
        final String logName = "Log" + testName
        final String logFileName = logName + ".html"
        
        MainBuild.run1C('reg_server')

        ArrayList parameterEpf = new ArrayList()
        parameterEpf.add('Base=%CD%\\' + MainBuild.baseFolder())
        parameterEpf.add(String.format('Specificity=%1$s\\Specificity.txt', s_pathToConfig))
        parameterEpf.add('Result=' + resultCode)
        parameterEpf.add('ReportHtml=' + resultName)

        final String path1C = MainBuild.getPath1C()
        if (path1C != ''){
            parameterEpf.add('Path=' + path1C + '\\bin')
        }
        if ((MainBuild.getUseChangeObjects()) && (MainBuild.s_script.fileExists(MainBuild.fileChangeObject()))) {
            if (getFileChangeObjectForCodeAnalysis(MainBuild.fileChangeObject())){
                parameterEpf.add('Objects=' + s_changeObjects)
            }
        }
        final String fileNameIgnoreObject = s_pathToConfig + '\\IgnoreObjects.txt'
        if (MainBuild.s_script.fileExists(fileNameIgnoreObject)) {
            parameterEpf.add('IgnoreObjects=' + fileNameIgnoreObject)
        }

        parameterEpf.add(String.format('IgnoreErrors=%1$s\\IgnoreErrors.txt', s_pathToConfig))
        
        MainBuild.run1C(String.format('start %1$s "%2$s"', s_command, parameterEpf.join(';')), s_baseAcc, logFileName, true)
        MainBuild.publishResultHTML(logName, logFileName)

        Boolean publishResult = false

        if (MainBuild.getResultFromFile(resultCode) == '0') {
            echoEmptyReport()
        }else{
            publishResult = true
        }

        if (publishResult){
            MainBuild.setUnstableResult(getClassName())
            MainBuild.publishResultHTML(getClassName(), resultName)
        }

    }

    private static Boolean getFileChangeObjectForCodeAnalysis(final String fileName){
        
        Boolean createFile = false

        String text = MainBuild.getTextFromFile(fileName)
        ArrayList newLines = new ArrayList()
        def matcher = text =~ /cf\/.+\/.+/
        while(matcher.find()) {
            String fullPath = text.substring(matcher.start() + 3, matcher.end())
            ArrayList array = fullPath.split("/")
            newLines.add(GetObjectName(array[0]) + "." + array[1])
        }
        
        if (newLines.size()>0){
            MainBuild.writeTextToFile(s_changeObjects, newLines.join(System.lineSeparator()))
            createFile = true
        }

        return createFile

    }

    private static String GetObjectName(final String objectPath){
        
        String objectName = ""

        switch(objectPath) {
            case "BusinessProcesses":
                objectName = "БизнесПроцессы"
                break
            case "Documents":
                objectName = "Документы"
                break
            case "DocumentJournals":
                objectName = "ЖурналыДокументов"
                break
            case "Tasks":
                objectName = "Задачи"
                break
            case "Constants":
                objectName = "Константы"
                break
            case "DataProcessors":
                objectName = "Обработки"
                break
            case "WebServices":
                objectName = "WebСервисы"
                break
            case "FilterCriteria":
                objectName = "КритерииОтбора"
                break
            case "CommonModules":
                objectName = "ОбщиеМодули"
                break
            case "CommonForms":
                objectName = "ОбщиеФормы"
                break
            case "ExchangePlans":
                objectName = "ПланыОбмена"
                break
            case "Reports":
                objectName = "Отчеты"
                break
            case "Enums":
                objectName = "Перечисления"
                break
            case "ChartsOfCharacteristicTypes":
                objectName = "ПланыВидовХарактеристик"
                break
            case "AccumulationRegisters":
                objectName = "РегистрыНакопления"
                break
            case "InformationRegisters":
                objectName = "РегистрыСведений"
                break
            case "Catalogs":
                objectName = "Справочники"
                break
            default:
                objectName = 'error'
        }

        return objectName

    }

}

enum UnitTestType {
    THIN('Thin'),
    THICK('Thick')

    public final String m_name

    private UnitTestType(String name) {
        m_name = name
    }
}

class UnitTest extends TestCase{

    private final UnitTestType m_type
    private final String m_extensions
    private final String m_tests
    private final String m_stashNameForExt

    UnitTest(final String name, final String node, final UnitTestType type, final String tests, final String extensions = ''){
        
        super(name, node)
        
        m_type = type
        m_extensions = extensions
        m_tests = tests
        m_stashNameForExt = name + 'Ext'

    }

    void getResources(){
        
        if (!MainBuild.resourceExist(getClassName())) {
            s_script.copyArtifacts(fingerprintArtifacts: true, projectName: 'add')
            MainBuild.stashResource(getClassName(), 'xddTestRunner.epf, plugins\\*.epf')
        }

        ArrayList stashStringTest = new ArrayList()
        for(String testName: m_tests.split(',')) {
            stashStringTest.add('build\\' + testName.replace('\\Ext\\ObjectModule.bsl', '.epf'))
        }
        MainBuild.stashResource(getName(), stashStringTest.join(', '))

        if (m_extensions != ''){

            ArrayList stashStringExt = new ArrayList()
            for(String extName: m_extensions.split(',')) {
                stashStringExt.add(String.format( '%1$s\\%2$s\\**', 'spec\\ext', extName))
            }
            MainBuild.stashResource(m_stashNameForExt, stashStringExt.join(', '))

        }

    }

    protected void commandForRunTest(){

        MainBuild.unstashResource(MainBuild.baseFolder())
        MainBuild.unstashResource(getClassName())
        MainBuild.unstashResource(getName())

        final String workPath = s_script.pwd()
        
        if (m_extensions != ''){
            MainBuild.unstashResource(m_stashNameForExt)
            MainBuild.run1C(String.format('load_extension --agent --update --folder %1$s\\%2$s', workPath, 'spec\\ext'),
                            MainBuild.baseFolder())
        }

        s_script.copyArtifacts(filter: 'xddTestRunner.epf, plugins\\*.epf', fingerprintArtifacts: true, projectName: 'add')
        final String resultName = getName().toLowerCase() + ".xml"

        ArrayList partOfText = new ArrayList()
        partOfText.add('"start"')

        if (m_type == UnitTestType.THICK){
            partOfText.add('"--thick"')    
        }

        partOfText.add('"--connection"')
        partOfText.add('"File=%1$s"')
        partOfText.add('"--epf"')
        partOfText.add('"%2$s\\xddTestRunner.epf"')
        partOfText.add('"--options"')
        partOfText.add('"xddRun ЗагрузчикКаталога %2$s\\%3$s;xddReport ГенераторОтчетаJUnitXML %2$s\\%4$s;xddShutdown;"')
        
        final String path1C = MainBuild.getPath1C()
        if (path1C != ''){
            partOfText.add('"--path"')
            partOfText.add(String.format('"%1$s"', path1C))
        }

        String textFile = String.format('[' + partOfText.join(',') + ']', MainBuild.baseFolder(), s_script.pwd(), 'build\\spec\\tests', resultName)
        MainBuild.debug('textFile: ' + textFile)

        String fileName = 'xUnit.json'
        MainBuild.writeTextToFile(fileName, textFile.replace('\\', '\\\\'))
        MainBuild.run1C('file --params ' + fileName, '', '', true)

        s_script.junit(resultName)

    }

}

enum BehaveTestType {
    THIN('Thin'),
    WEB('Web')

    public final String m_name

    private BehaveTestType(String name) {
        m_name = name
    }
}

class BehaveTest extends TestCase{

    private final BehaveTestType m_type
    private final String m_extensions
    private final String m_features
    private final String m_stashNameForExt
    private final String m_baseName
    private final String m_linkToBase
    private final String m_apacheDir
    private final String m_confName
    private final String m_confDir
    private final String m_pathToConf
    private final String m_commandStartApache
    private final String m_webDir
    private final String m_pathToApache

    BehaveTest(final String name, final String node, final BehaveTestType type, final String features, final String extensions = ''){
        
        super(name, node)
        
        m_type = type
        m_extensions = extensions
        m_features = features
        m_stashNameForExt = name + 'Ext'

        m_pathToApache = 'httpd.exe'
        m_baseName = 'BaseForTest'
        m_linkToBase = 'C:\\' + m_baseName
        m_apacheDir = 'C:\\Apache\\'
        m_confName = 'httpd.conf'
        m_confDir = m_apacheDir + 'conf'
        m_pathToConf = m_confDir + '\\' + m_confName
        m_commandStartApache = String.format('%1$sbin\\%2$s -w -f "%3$s" -d "%4$s."', m_apacheDir, m_pathToApache, m_pathToConf, m_apacheDir)
        m_webDir = 'c:\\web'

    }

    void getResources(){

        if (!MainBuild.resourceExist(getClassName())) {
            s_script.copyArtifacts(fingerprintArtifacts: true, projectName: 'add')
            MainBuild.stashResource(getClassName(), 'bddRunner.epf, lib/featurereader/vbFeatureReader.epf, features/libraries/**, locales/**, plugins/**, vendor/**')
        }

        ArrayList stashStringFeature = new ArrayList()
        for(String featureName: m_features.split(',')) {
            stashStringFeature.add(featureName)
            String epf = featureName.replace('.feature', '.epf')
            stashStringFeature.add(epf.replace('build\\spec\\features', 'build\\spec\\features\\step_definitions'))
        }
        MainBuild.stashResource(getName(), stashStringFeature.join(', '))

        if (m_extensions != ''){

            ArrayList stashStringExt = new ArrayList()
            for(String extName: m_extensions.split(',')) {
                stashStringExt.add(String.format( '%1$s\\%2$s\\**', 'spec\\ext', extName))
            }
            MainBuild.stashResource(m_stashNameForExt, stashStringExt.join(', '))

        }

    }

    protected void commandForRunTest(){

        MainBuild.unstashResource(MainBuild.baseFolder())
        MainBuild.unstashResource(getClassName())
        MainBuild.unstashResource(getName())

        final String workPath = s_script.pwd()
        final String workPathForJson = workPath.replace('\\', '\\\\')
        final String resultFolder = MainBuild.getResultFolder()

        if (m_extensions != ''){
            MainBuild.unstashResource(m_stashNameForExt)
            MainBuild.run1C(String.format('load_extension --agent --update --folder %1$s\\%2$s', workPath, 'spec\\ext'),
                            MainBuild.baseFolder())
        }

        String fileNameConfig = 'vanessa.json'
        final String configVanessa = """{
                                            "КаталогФич": "${workPathForJson}\\\\build\\\\spec\\\\features",
                                            "ДелатьОтчетВФорматеCucumberJson": "Истина",
                                            "КаталогOutputCucumberJson": "${workPathForJson}\\\\${resultFolder}",
                                            "ЗавершитьРаботуСистемы": "Истина",
                                            "ВыполнитьСценарии": "Истина",
                                            "СписокТеговИсключение": ["IgnoreOnCIMainBuild"]
                                        }
                                    """
        MainBuild.writeTextToFile(fileNameConfig, configVanessa)

        ArrayList partOfText = new ArrayList()
        partOfText.add('start --test_manager')
        partOfText.add('--epf bddRunner.epf')
        partOfText.add(String.format('--options "StartFeaturePlayer;VBParams=%1$s\\%2$s"', workPath, fileNameConfig))

        if (m_type==BehaveTestType.WEB){

            MainBuild.startBat(String.format('copy %1$s %2$s || exit 0', m_pathToConf, workPath))
            MainBuild.startBat(String.format('MKLINK /D %1$s %2$s\\%3$s || exit 0', m_linkToBase, workPath, MainBuild.baseFolder()))

            MainBuild.run1C(String.format('webinst --wsdir %1$s --dir %2$s --confpath %3$s', m_baseName, m_webDir, m_pathToConf), MainBuild.baseFolder())

            changeAlias(m_pathToConf, m_baseName)
            MainBuild.startBat('start ' + m_commandStartApache)

        }

        MainBuild.run1C(partOfText.join(' '), MainBuild.baseFolder())

        final String fileName = 'CucumberJson.json'
        final String newFileName = java.util.UUID.randomUUID().toString() + '.json'
        MainBuild.startBat(String.format('move %1$s\\%2$s %1$s\\%3$s', resultFolder, fileName, newFileName))

        MainBuild.stashResource(getStashNameForResult(), resultFolder + '/**')

    }

    protected void commandAfterTest(){

        MainBuild.startBat('taskkill /IM 1cv8c.exe /F || exit 0')
        
        if (m_type==BehaveTestType.WEB){

            MainBuild.startBat('taskkill /IM chrome.exe /F || exit 0')
            MainBuild.startBat('taskkill /IM ' + m_pathToApache + ' /F || exit 0')

            MainBuild.startBat('rmdir ' + m_linkToBase)
            MainBuild.startBat(String.format('copy %1$s\\%2$s %3$s /y || exit 0', s_script.pwd(), m_confName, m_confDir))
            MainBuild.startBat('rmdir ' + m_webDir + ' /S /Q')

            // todo найти ключ для запуска браузера без ошибок после принудительного завершения (пока просто очищаю кэш)
            MainBuild.startBat('rd /Q /S "%userprofile%\\AppData\\Local\\Google\\Chrome\\User Data\\Default"')

        }

    }

    // не делать static иначе не работает на jenkins
    private void changeAlias(final String pathToConf, final String oldAlias){

        final String text = MainBuild.getTextFromFile(pathToConf)
        MainBuild.writeTextToFile(pathToConf, text.replaceAll(oldAlias, ''))

    }

}

class SonarQube extends TestCase{

    SonarQube(final String name, final String node){
        super(name, node)
    }

    void getResources(){
        MainBuild.stashResource(getName(), 'sonar-project.properties, **/*.bsl')
    }

    protected void commandForRunTest(){
        
        MainBuild.unstashResource(getName())

        def scannerHome = s_script.tool(name: 'sonar', type: 'hudson.plugins.sonar.SonarRunnerInstallation');
        s_script.withSonarQubeEnv(installationName: 'sonar') {
            MainBuild.s_sonar_host_url = s_script.env.SONAR_HOST_URL
            ArrayList sonarcommand = new ArrayList()
            sonarcommand.add(String.format('%1$s/bin/sonar-scanner', scannerHome))
            sonarcommand.add(String.format('-Dsonar.branch.name=%1$s -Dsonar.projectKey=%2$s', MainBuild.getBranch(), MainBuild.getRepo()))
            MainBuild.startBat(sonarcommand.join(' '))
        }

    }

}

return this
