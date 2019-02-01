
// todo для освобождения ноды при CodeAnalysis можно попробовать использовать ещё одну сборку с передачей параметров ей и забиранием результата

//методы для jenkins

def setScript(final script){
    MainBuild.s_script = script
    //noinspection GroovyAssignabilityCheck
    MainBuild.s_libScript = this
}

def addTest(final String name, final String node = ''){
    MainBuild.addTest(name, node)
}

def startBuild(){
    MainBuild.startBuild()
}

def setNode(final String node){
    //noinspection GroovyAssignabilityCheck
    MainBuild.s_node = node
}

def setPathToSource(final repo, final branch, final bitbucket = false){
    MainBuild.s_repo = repo
    MainBuild.s_branch = branch
    MainBuild.s_bitbucket = bitbucket
    //noinspection GroovyAssignabilityCheck
    MainBuild.s_multibranch = false
}

def setRunModeOrdinaryApplication(){
    //noinspection GroovyAssignabilityCheck
    MainBuild.s_runModeOrdinaryApplication = true
}

def setFixturesNotExists(){
    //noinspection GroovyAssignabilityCheck
    MainBuild.s_fixturesExists = false
}

def setPath1C(final path1C){
    MainBuild.s_path1C = path1C
}

def setDebugMode(){
    MainBuild.s_debug = true
    MainBuild.s_sendMsg = false
    //noinspection GroovyAssignabilityCheck
    disableScanTask()
}

def disableScanTask(){
    //noinspection GroovyAssignabilityCheck
    MainBuild.s_scanTask = false
}

def setArtifactsPath(final String path){
    //noinspection GroovyAssignabilityCheck
    MainBuild.s_artifactsPath = path
}

//ядро

final class MainBuild{

    public static s_script
    public static s_libScript
    public static String s_node = ''
    public static String s_repo = ''
    public static String s_branch = ''
    public static String s_path1C = ''
    public static String s_artifactsPath = ''
    public static Boolean s_multibranch = true
    public static Boolean s_bitbucket = false
    public static Boolean s_debug = false
    public static Boolean s_sendMsg = true
    public static Boolean s_runModeOrdinaryApplication = false
    public static Boolean s_fixturesExists = true
    public static Boolean s_scanTask = true
    private static String s_baseFolder = 'base'
    private static String s_stashNameTaskFor1C = 'TaskFor1C'
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

            //noinspection GroovyVariableCanBeFinal
            try {

                s_script.timestamps {

                    s_script.deleteDir()

                    getSource()
                    copyIgnoreObject()
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
                //noinspection GroovyAssignabilityCheck
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
                s_script.node('master') {
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

            //noinspection GroovyAssignabilityCheck
            debug('stash: ' + name, 32)
            //noinspection GroovyAssignabilityCheck
            debug('path: ' + path, 32)

            s_script.stash(includes: path, name: name)
            s_existResources.add(name)

        }else{
            //noinspection GroovyAssignabilityCheck
            debug('exist stash: ' + name, 32)
        }

    }

    static void unstashResource(final String name){

        //noinspection GroovyAssignabilityCheck
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

        //noinspection GroovyAssignabilityCheck
        debug('startBat: ' + text)
        s_script.bat(text)

    }

    static void startBatСyrillic(final String text){

        //noinspection GroovyAssignabilityCheck
        debug('startBatСyrillic: ' + text)
        s_script.bat('''chcp 65001 >NUL
                        set x=''' + text + '''
                        chcp 866 >NUL
                        %x%''')

    }

    static void run1C(final String command, final String base = '', final String log = '', final boolean notUsePath=false){

        ArrayList partOfText = new ArrayList()
        partOfText.add('python TaskFor1C.py --timeout 0')

        if (!notUsePath && s_path1C != ''){
            partOfText.add(String.format('--path "%1$s"', s_path1C))
        }

        if (s_debug) {
            partOfText.add('--debug')
        }

        if (log != ''){
            partOfText.add(String.format('--log "%1$s"', log))
        }

        partOfText.add(command)

        if (base != ""){
            partOfText.add(String.format('--connection "File=%1$s"', base))
        }

        s_script.timeout(90) {
            //noinspection GroovyAssignabilityCheck
            startBat(partOfText.join(' '))
        }

    }

    static String getTextFromFile(final String pathToFile){
        return s_script.readFile(pathToFile)
    }

    static void writeTextToFile(final String pathToFile, final String text){

        s_script.writeFile(file: pathToFile, text: text)
        setUtfEncoding(pathToFile)

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

    static String taskFor1C(){
        return s_stashNameTaskFor1C
    }

    static String fileChangeObject(){
        return s_fileСhangeObject
    }

    static void deleteWorkspace(){

        //noinspection UnnecessaryQualifiedReference
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

        //noinspection GroovyAssignabilityCheck
        startBat('mkdir ' + pathToResult)
        //noinspection GroovyAssignabilityCheck
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

        //noinspection GrUnnecessaryDefModifier
        final def files = s_script.findFiles(glob: pathToFolder + '//*.xml')
        for(int count = 0; count < files.size(); count++) {
            //noinspection GroovyAssignabilityCheck
            if (findErrorInFile(testName, files[count].path, true)){
                //noinspection GroovyBreak
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

        //noinspection GroovyAssignabilityCheck
        //s_script.currentBuild.description = addTextToString(s_script.currentBuild.description, testName)

    }

    private static void sortTestsByNode(){

        //noinspection GroovyVariableCanBeFinal
        ArrayList newTests = new ArrayList()
        //noinspection GroovyVariableCanBeFinal,GroovyAssignabilityCheck
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
                        )
                ]
                )

                s_script.checkout(s_script.scm)

            }else{
                if (s_bitbucket){
                    String account = 'VakulenkoAleksei'
                    s_script.git(branch: s_branch, credentialsId: '018f9d07-8f7c-41c5-9fb9-e041b1ee72b0', url: String.format('https://%1$s@bitbucket.org/pharmsklad/%2$s.git', account, s_repo))
                }else{
                    s_script.git(branch: s_branch, credentialsId: '63bc67e4-1791-4a5c-b17e-5625d41dbdcd', url: String.format('git@w-git:%1$s.git', s_repo))
                }

            }

        }

    }

    private static void copyIgnoreObject(){

        String fileName = 'IgnoreObject.txt'
        String filePath = Folders.SYNTAX.m_path + '\\' + fileName
        String copyCommand = 'copy %1$s %2$s'
        if (s_script.fileExists(filePath)){
            //noinspection GroovyAssignabilityCheck
            startBat(String.format(copyCommand, filePath, Folders.SYNTAX_ACC.m_path + '\\' + fileName))
            //noinspection GroovyAssignabilityCheck
            startBat(String.format(copyCommand, filePath, Folders.SYNTAX_PLATFORM.m_path + '\\' + fileName))
        }

    }

    private static void createBase(){

        s_script.stage('CreateBase'){

            s_script.step(
                    [
                            $class: 'CopyArtifact',
                            filter: 'build/epf/CloseAfterUpdate.epf',
                            fingerprintArtifacts: true,
                            flatten: true,
                            projectName: 'Tools'
                    ]
            )

            s_script.step(
                    [
                            $class: 'CopyArtifact',
                            filter: 'TaskFor1C.py, commands\\*',
                            fingerprintArtifacts: true,
                            projectName: 'TaskFor1C python3'
                    ]
            )

            final String workPath = s_script.pwd()
            ArrayList partOfText = new ArrayList()
            partOfText.add(String.format('ci --folder "%1$s\\%2$s" --epf CloseAfterUpdate.epf', workPath, Folders.CF.m_path))
            partOfText.add(String.format('--build_epf "%1$s"', workPath))
            if (s_runModeOrdinaryApplication) {
                partOfText.add('--thick')
            }
            if (s_fixturesExists){
                partOfText.add(String.format('--fixtures "%1$s\\%2$s"', workPath, Folders.FIXTURES.m_path))
            }

            //noinspection GroovyAssignabilityCheck
            run1C(partOfText.join(' '), baseFolder())

            String filePath = baseFolder() + '/1Cv8.1CD'
            saveChangeFiles()

            if (s_script.fileExists(fileChangeObject())) {
                filePath += ', ' + fileChangeObject()
            }

            //noinspection GroovyAssignabilityCheck
            stashResource(baseFolder(), filePath)
            stashResource(taskFor1C(), 'TaskFor1C.py, commands\\*')

        }

    }

    private static void getTask(){

        s_script.recordIssues(
            blameDisabled: true,
            ignoreQualityGate: true,
            sourceCodeEncoding: 'UTF-8',
            tools: [s_script.taskScanner(
                        excludePattern: Folders.CF.m_path + 'DataProcessors/xddTestRunner/**/*.bsl',
                        highTags: 'fixme',
                        ignoreCase: true,
                        includePattern: '**/*.bsl',
                        normalTags: 'todo'
                    )]
        )

    }

    private static void getResourcesForTest(){

        s_script.stage('GetResources'){

            //noinspection GroovyAssignabilityCheck
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
                //noinspection GroovyAssignabilityCheck
                showError(exception)
            }


        }

    }

    private static void archiveArtifacts(final String stashName){
        if ((s_artifactsPath != '') && (s_script.currentBuild.result == "SUCCESS")) {
            s_script.node('master') {

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

    private static void setUtfEncoding(String pathToFile) {
        if (!s_script.fileExists('ChangeEncoding.py')) {
            s_script.step(
                    [
                            $class              : 'CopyArtifact',
                            filter              : 'script/ChangeEncoding.py',
                            fingerprintArtifacts: true,
                            flatten             : true,
                            projectName         : 'Tools'
                    ]
            )
        }

        //noinspection GroovyAssignabilityCheck
        startBat('python ChangeEncoding.py --file ' + pathToFile)
    }

    private static void saveChangeFiles() {
        ArrayList changeFiles = s_libScript.getChangeFiles()
        if (changeFiles.size() > 0) {
            //noinspection GroovyAssignabilityCheck
            writeTextToFile(s_fileСhangeObject, changeFiles.join(System.lineSeparator()))
        }
    }

    private static void getTestFromName() {

        //noinspection GroovyAssignabilityCheck
        for(object in s_testName) {
            //noinspection GroovyIfStatementWithTooManyBranches
            if (object.name == 'UnitTest') {
                //noinspection GroovyAssignabilityCheck
                s_tests.add(new UnitTest(object.name, object.node))
            }else if (object.name == 'PlatformCheck') {
                //noinspection GroovyAssignabilityCheck
                s_tests.add(new PlatformCheck(object.name + 'Extended', object.node, true))
                //noinspection GroovyAssignabilityCheck
                s_tests.add(new PlatformCheck(object.name + 'Simple', '', false))
            }else if (object.name == 'CodeAnalysis') {
                //noinspection GroovyAssignabilityCheck
                s_tests.add(new CodeAnalysis(object.name + 'Split', object.node, true))
            }else if (object.name == 'CodeAnalysisFull') {
                //noinspection GroovyAssignabilityCheck
                s_tests.add(new CodeAnalysis(object.name, object.node, false))
            }else if (object.name == 'BehaveTest') {

                // todo вынести в отдельную функцию получение файлов и тегов

                ArrayList fileName = new ArrayList()
                ArrayList tagName = new ArrayList()
                def filesTags = [:]
                ArrayList ignoreTags = ['tree']

                def files = s_script.findFiles(glob: Folders.FEATURES.m_path + '/*.feature')
                for(int count = 0; count < files.size(); count++) {
                    String text = getTextFromFile(files[count].path)
                    ArrayList tags = new ArrayList()
                    def matcher = text =~ /@.+/
                    while(matcher.find()) {
                        String tag = text.substring(matcher.start() + 1, matcher.end())
                        if (!(tag in ignoreTags)){
                            tags.add(tag)
                        }
                    }
                    filesTags.put(files[count].name.replace('.feature', ''), tags.join(','))
                }

                for (ext in filesTags.values().unique()){
                    ArrayList file = new ArrayList()
                    for(featureTag in filesTags) {
                        if (featureTag.value == ext){
                            file.add(featureTag.key)
                        }
                    }
                    fileName.add(file.join(','))
                    tagName.add(ext)
                }

                for(int count = 0; count < fileName.size(); count++) {

                    // todo придумать правило формирование имени чтобы было более читаемо

                    String tag = tagName[count]
                    String file = fileName[count]

                    BehaveTestType type = BehaveTestType.THIN
                    if (tag.contains(BehaveTestType.WEB.m_name)){
                        type = BehaveTestType.WEB
                    }else if (tag.contains(BehaveTestType.THICK.m_name)){
                        type = BehaveTestType.THICK
                    }
                    tag = deleteTag(tag, type.m_name)
                    tag = tag.replace('Расширение', '')
                    String name = 'BehaveTest' + type.m_name + tag.replace(',', '')

                    //noinspection GroovyAssignabilityCheck
                    debug('BehaveTest name = ' + name)
                    debug('BehaveTest type = ' + type.m_name)
                    debug('BehaveTest features = ' + file)
                    debug('BehaveTest extensions = ' + tag)

                    s_tests.add(new BehaveTest(name, '', type, file, tag))

                }

            }
        }
        sortTestsByNode()
    }

    private static String deleteTag(String tags, String tag) {
        tags = tags.replace(tag + ',', '')
        return tags.replace(tag, '')
    }

    private static void getResult(){

        s_script.deleteDir()

        Boolean BehaveTestExists = false

        for(TestCase object: s_tests) {
            if (!object.getPublishResult()){
                unstashResource(object.getStashNameForResult())
                if (object.getClassName() == 'BehaveTest'){
                    BehaveTestExists = true
                }
            }
        }

        if (BehaveTestExists){
            String resultFolder = getResultFolder()
            //s_script.allure(includeProperties: false, jdk: '', results: [[path: resultFolder]])
            //setResultAllure('Allure', resultFolder)
            s_script.cucumber(buildStatus: 'UNSTABLE',
                                failedStepsNumber: 1,
                                fileIncludePattern: '**/*.json',
                                jsonReportDirectory: resultFolder,
                                sortingMethod: 'ALPHABETICAL')
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
            //noinspection GroovyAssignmentToMethodParameter
            string += ', ' + text
        }else{
            //noinspection GroovyAssignmentToMethodParameter
            string = text
        }

        return string

    }

    private static void showError(exception) {
        echoColourText('ERROR: ' + exception.getMessage(), 31)
    }

}

enum Folders{

    SYNTAX('spec\\syntax'),

    CF('cf'),
    EXT('lib\\ext'),
    FEATURES('build\\spec\\features'),
    FIXTURES('build\\spec\\fixtures'),
    SYNTAX_ACC(SYNTAX.m_path + '\\acc' as String),
    SYNTAX_PLATFORM(SYNTAX.m_path + '\\platform' as String),
    TESTS_UNIT_4('build\\spec\\tests' as String),

    public final String m_path

    private Folders(String path){
        m_path = path
    }
}

//не переносить в класс иначе не работает parallel
def runTests(final tests){

    //noinspection GroovyVariableCanBeFinal,GroovyAssignabilityCheck
    def stepsForParallel = [:]

    //noinspection GroovyVariableCanBeFinal,GroovyAssignabilityCheck
    for(TestCase object: tests) {
        //noinspection GroovyAssignabilityCheck
        stepsForParallel[object.getName()] = object.runTest()
    }

    stepsForParallel.failFast = true
    parallel(stepsForParallel)

}

@NonCPS
def getChangeFiles(){

    ArrayList changeFiles = new ArrayList()
    def changeLogSets = currentBuild.changeSets

    //noinspection GroovyVariableNotAssigned
    for (int i = 0; i < changeLogSets.size(); i++) {

        //noinspection GroovyAssignabilityCheck
        def entries = changeLogSets[i].items

        //noinspection GroovyVariableNotAssigned
        for (int j = 0; j < entries.length; j++) {

            //noinspection GroovyAssignabilityCheck
            def files = entries[j].affectedFiles

            for (int k = 0; k < files.size(); k++) {
                //noinspection GroovyVariableNotAssigned,GroovyAssignabilityCheck
                changeFiles.add(files[k].path)
            }

        }

    }

    //noinspection GroovyAssignabilityCheck,GroovyVariableNotAssigned
    return changeFiles

}

abstract class TestCase implements Serializable{

    public static s_script
    protected final String m_node
    protected final String m_name
    protected final Boolean m_publishResult

    TestCase(final String name, final String node, final Boolean publishResult = true){
        m_node = node
        m_name = name
        m_publishResult = publishResult
    }

    def runTest(){
        //noinspection GroovyAssignabilityCheck
        return {

            s_script.node(m_node) {

                s_script.timestamps {

                    Boolean errorExist = false

                    //noinspection GroovyVariableCanBeFinal
                    try {

                        s_script.deleteDir()
                        MainBuild.unstashResource(MainBuild.taskFor1C())
                        commandForRunTest()

                    }catch (exception) {
                        errorExist = true
                        //noinspection GroovyAssignabilityCheck
                        MainBuild.setResultAfterError(exception)
                    } finally {

                        commandAfterTest()

                        if (!MainBuild.s_debug) {
                            try {
                                MainBuild.deleteWorkspace()
                            }catch (exception) {
                                errorExist = true
                                //noinspection GroovyAssignabilityCheck
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

    Boolean getPublishResult(){
        return m_publishResult
    }

    String getClassName(){
        return getClass().getName()
    }

    String getStashNameForResult(){
        return m_name + 'result'
    }

    void getResources(){
        //noinspection GroovyAssignabilityCheck
        MainBuild.debug(m_name + '_getResources')
    }

    protected void commandForRunTest(){
        //noinspection GroovyAssignabilityCheck
        MainBuild.debug(m_name + '_commandForRunTest')
    }

    protected void commandAfterTest(){
        //noinspection GroovyAssignabilityCheck
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
        m_pathToConfig = Folders.SYNTAX_PLATFORM.m_path
    }

    void getResources(){
        //noinspection GroovyAssignabilityCheck
        MainBuild.stashResource(getClassName(), m_pathToConfig + '/*')
    }

    protected void commandForRunTest(){

        MainBuild.unstashResource(MainBuild.baseFolder())
        MainBuild.unstashResource(getClassName())

        final String testName = getName()
        final String resultName = testName + ".html"

        ArrayList partOfText = new ArrayList()
        partOfText.add('check --options')

        if (m_extendedModulesCheck) {

            if (MainBuild.getRunModeOrdinaryApplication()) {
                partOfText.add('" -ExtendedModulesCheck"') //не убирать пробел в начале иначе не работает запуск в python
            } else {
                partOfText.add('"-ExtendedModulesCheck -CheckUseModality"')
                partOfText.add(String.format('--skip_modality %1$s\\SkipModality.txt', m_pathToConfig))
            }

        } else {
            
            partOfText.add('"-ConfigLogIntegrity -IncorrectReferences -ThinClient -Server')
                        
             if (!MainBuild.getRunModeOrdinaryApplication()) {
                partOfText.add('-WebClient')
            }

            partOfText.add('-ExternalConnection -ExternalConnectionServer -ThickClientOrdinaryApplication')
            partOfText.add('-ThickClientServerOrdinaryApplication -UnreferenceProcedures -HandlersExistence')
            partOfText.add('-EmptyHandlers"')
            
        }

        partOfText.add(String.format('--skip_error %1$s\\IgnoreError.txt --skip_object %1$s\\IgnoreObject.txt', m_pathToConfig))
        //noinspection GroovyAssignabilityCheck
        MainBuild.run1C(partOfText.join(' '), MainBuild.baseFolder(), resultName, false)

        if (MainBuild.getTextFromFile(resultName) != '') {
            MainBuild.setUnstableResult(testName)
            MainBuild.publishResultHTML(testName, resultName)
        }else{
            echoEmptyReport()
        }

    }

}

enum CodeAnalysisType {
    PART1('Part1', 'Part1.txt', 'fast'),
    PART2('Part2','Part2.txt', 'fast'),
    PART3('Part3','Part3.txt', 'fast'),
    PART4('Part4','Part4.txt'),
    OTHERS('Others')

    public final String m_name
    public final String m_filename
    public final String m_node

    private CodeAnalysisType(String name, String filename = '', String node = '') {
        m_name = name
        m_filename = filename
        m_node = node
    }

}

class CodeAnalysis extends TestCase{

    static final String s_command = '--thick --epf SyntaxCheckAcc.epf --options'
    static final String s_baseAcc = 'base_acc'
    static final String s_stashName = 'CodeAnalysis'
    static final String s_pathToConfig = Folders.SYNTAX_ACC.m_path
    private final Boolean m_split

    CodeAnalysis(final String name, final String node, final Boolean split){
        //noinspection GroovyAssignabilityCheck
        super(name, node)
        m_split = split
     }

    void getResources(){

        if (!MainBuild.resourceExist(s_stashName)) {

            if (MainBuild.s_repo != 'Acc') {
                s_script.step(
                        [
                                $class              : 'CopyArtifact',
                                filter              : 'base/1Cv8.1CD',
                                fingerprintArtifacts: true,
                                flatten             : true,
                                projectName         : 'Acc',
                                target              : s_baseAcc
                        ]
                )
            }else{
                //noinspection GroovyAssignabilityCheck
                MainBuild.startBat(String.format('echo f|xcopy /y "%1$s\\1Cv8.1CD" "%2$s\\1Cv8.1CD" >nul', MainBuild.baseFolder(), s_baseAcc))
            }

            if (MainBuild.s_repo != 'Acc') {
                s_script.step(
                        [
                                $class              : 'CopyArtifact',
                                filter              : 'build/lib/epf/SyntaxCheckAcc.epf, lib/config/split/**',
                                fingerprintArtifacts: true,
                                flatten             : true,
                                projectName         : 'Acc'
                        ]
                )
            }else{
                MainBuild.startBat('copy build\\lib\\epf\\SyntaxCheckAcc.epf SyntaxCheckAcc.epf || exit 0')
                MainBuild.startBat('xcopy lib\\config\\split\\* %CD% || exit 0')
            }

            final String resource = String.format('%1$s/*, %2$s/1Cv8.1CD, SyntaxCheckAcc.epf, Part*.txt, CreateBase.txt', s_pathToConfig, s_baseAcc)
            //noinspection GroovyAssignabilityCheck
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
            parameterEpf.add('Path=' + path1C)
        }
        //if (MainBuild.s_script.fileExists(MainBuild.fileChangeObject())) {
        //    parameterEpf.add('Object=' + MainBuild.fileChangeObject())
        //}
        final String fileNameIgnoreObject = s_pathToConfig + '\\IgnoreObject.txt'
        if (MainBuild.s_script.fileExists(fileNameIgnoreObject)) {
            parameterEpf.add('IgnoreObject=' + fileNameIgnoreObject)
        }

        if (m_split) {
            parameterEpf.add('Requirements=CreateBase.txt')
        }else {
            parameterEpf.add(String.format('IgnoreRequirements=%1$s\\IgnoreRequirements.txt', s_pathToConfig))
        }

        //noinspection GroovyAssignabilityCheck
        MainBuild.run1C(String.format('start %1$s "%2$s"', s_command, parameterEpf.join(';')), s_baseAcc, logFileName, true)
        MainBuild.publishResultHTML(logName, logFileName)

        Boolean publishResult = false

        if (MainBuild.getResultFromFile(resultCode) == '0') {

            if (m_split) {

                //noinspection GroovyAssignabilityCheck
                MainBuild.stashResource(s_baseAcc, s_baseAcc + '/*')

                ArrayList PartOfAnalysis = getPartOfAnalysis()
                MainBuild.s_libScript.runTests(PartOfAnalysis)

                // todo создавать единую таблицу с выравниванием колонок

                ArrayList collectLines = new ArrayList()
                Boolean splitPublishResult = false

                for(TestCase object: PartOfAnalysis) {

                    String objectName = object.getName()

                    MainBuild.unstashResource(objectName)

                    if (MainBuild.getResultFromFile(objectName + '.txt') == '0') {
                        object.echoEmptyReport()
                    }else{
                        ArrayList lines = MainBuild.getTextFromFile(objectName + '.html').split(System.lineSeparator())
                        collectLines += lines
                        splitPublishResult = true
                    }

                }

                if (splitPublishResult){
                    MainBuild.writeTextToFile(resultName, collectLines.join(System.lineSeparator()))
                    publishResult = true
                }

            }else{
                echoEmptyReport()
            }

        }else{
            publishResult = true
        }

        if (publishResult){
            MainBuild.setUnstableResult(getClassName())
            MainBuild.publishResultHTML(getClassName(), resultName)
        }

    }

    private ArrayList getPartOfAnalysis() {

        ArrayList tests = new ArrayList()

        //noinspection GroovyAssignabilityCheck
        CodeAnalysisType[] valueEnum = CodeAnalysisType.values()
        for (int i = 0; i < valueEnum.size(); i++) {
            //noinspection GroovyAssignabilityCheck
            CodeAnalysisType element = valueEnum[i]
            tests.add(new CodeAnalysisSplit(element.m_node, element))
        }

        return tests

    }

}

class CodeAnalysisSplit extends TestCase{

    private final CodeAnalysisType m_type

    CodeAnalysisSplit(final String node, final CodeAnalysisType type){
        //noinspection GroovyAssignabilityCheck
        super('CodeAnalysis' + type.m_name, node)
        m_type = type
    }

    protected void commandForRunTest(){

        MainBuild.unstashResource(CodeAnalysis.s_stashName)
        MainBuild.unstashResource(CodeAnalysis.s_baseAcc)

        final String resultName = getName() + ".html"
        final String resultCode = getName() + ".txt"
        final String logName = "Log" + getName()
        final String logFileName = logName + ".html"

        ArrayList parameterEpf = new ArrayList()
        parameterEpf.add('NotCollectBase=1')
        parameterEpf.add('ReportHtml=' + resultName)
        parameterEpf.add('Result=' + resultCode)

        if (m_type.m_filename != '') {

            parameterEpf.add('Requirements=' + m_type.m_filename)
            parameterEpf.add(String.format('IgnoreRequirements=%1$s\\IgnoreRequirements.txt', CodeAnalysis.s_pathToConfig))

        }else{

            final String pathToFile = String.format('%1$s\\IgnoreRequirements.txt', CodeAnalysis.s_pathToConfig)
            ArrayList requirements = getRequirementsFromPartFile(pathToFile)

            String fileName = 'requirements.txt'
            //noinspection GroovyAssignabilityCheck
            MainBuild.writeTextToFile(fileName, requirements.join(System.lineSeparator()))
            parameterEpf.add('IgnoreRequirements=' + fileName)

        }

        //noinspection GroovyAssignabilityCheck
        MainBuild.run1C(String.format('start %1$s "%2$s"', CodeAnalysis.s_command, parameterEpf.join(';')), CodeAnalysis.s_baseAcc, logFileName, true)
        MainBuild.publishResultHTML(logName, logFileName)

        MainBuild.stashResource(getName(), resultName + ', ' + resultCode)

    }

    private ArrayList getRequirementsFromPartFile(String pathToFile) {

        ArrayList requirements = MainBuild.getTextFromFile(pathToFile).split(System.lineSeparator())
        //noinspection GroovyAssignabilityCheck
        CodeAnalysisType[] valueEnum = CodeAnalysisType.values()
        for (int i = 0; i < valueEnum.size(); i++) {
            CodeAnalysisType element = valueEnum[i]
            if (element.m_filename != '') {
                ArrayList array = MainBuild.getTextFromFile(element.m_filename).split(System.lineSeparator())
                requirements += array
            }
        }
        requirements.unique()

        return requirements

    }

}

class UnitTest extends TestCase{

    private final String m_pathToTest

    UnitTest(final String name, final String node){
        super(name, node)
        m_pathToTest = Folders.TESTS_UNIT_4.m_path
    }

    void getResources(){
        //noinspection GroovyAssignabilityCheck
        MainBuild.stashResource(getName(), m_pathToTest + '/*')
    }

    protected void commandForRunTest(){

        MainBuild.unstashResource(MainBuild.baseFolder())
        MainBuild.unstashResource(getName())

        s_script.step(
                [
                        $class: 'CopyArtifact',
                        filter:  'xddTestRunner.epf, Plugins\\*.epf',
                        fingerprintArtifacts: true,
                        projectName: 'xUnitFor1C'
                ]
        )

        final String resultName = getName().toLowerCase() + ".xml"

        String textFile = String.format('["start", "--connection", "File=%1$s", "--epf", ' +
                                    '"%2$s\\xddTestRunner.epf", "--options", "xddRun ЗагрузчикКаталога %2$s\\%3$s;' +
                                    'xddReport ГенераторОтчетаJUnitXML %2$s\\%4$s;xddShutdown;"]',
                                    MainBuild.baseFolder(), s_script.pwd(), m_pathToTest, resultName)

        String fileName = 'xUnit.json'
        //noinspection GroovyAssignabilityCheck
        MainBuild.writeTextToFile(fileName, textFile.replace('\\', '\\\\'))
        //noinspection GroovyAssignabilityCheck
        MainBuild.run1C('file --params ' + fileName)

        s_script.junit(resultName)

    }

}

enum BehaveTestType {
    THICK('Thick'),
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
    private final String m_stashNameForToolsExt
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

        super(name, node, false)
        m_type = type
        m_extensions = extensions
        m_features = features
        m_stashNameForToolsExt = 'BehaveTestToolsExt'
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

            s_script.step(
                    [
                            $class              : 'CopyArtifact',
                            fingerprintArtifacts: true,
                            projectName         : 'Vanessa-behavior'
                    ]
            )

            //noinspection GroovyAssignabilityCheck
            MainBuild.stashResource(getClassName(), 'vanessa-behavior.epf, lib/FeatureReader/vbFeatureReader.epf,' +
                    ' features/Libraries/**, locales/**, plugins/**, vendor/**')

        }

        ArrayList stashStringFeature = new ArrayList()
        for(String featureName: m_features.split(',')) {
            stashStringFeature.add(String.format( '%1$s\\%2$s', Folders.FEATURES.m_path, featureName + '.feature'))
            stashStringFeature.add(String.format( '%1$s\\step_definitions\\%2$s', Folders.FEATURES.m_path, featureName + '.epf'))
        }
        MainBuild.stashResource(getName(), stashStringFeature.join(', '))

        if (m_extensions != ''){

            if (!MainBuild.resourceExist(m_stashNameForToolsExt)) {
                s_script.step(
                        [
                                $class              : 'CopyArtifact',
                                filter              : 'build/epf/ChangeSafeModeForExtension.epf',
                                fingerprintArtifacts: true,
                                projectName         : 'Tools'
                        ]
                )
                s_script.step(
                        [
                                $class              : 'CopyArtifact',
                                filter              : 'ext/**',
                                fingerprintArtifacts: true,
                                projectName         : 'Extension'
                        ]
                )
                MainBuild.stashResource(m_stashNameForToolsExt, 'ext/**, build/epf/ChangeSafeModeForExtension.epf')
            }

            ArrayList stashStringExt = new ArrayList()
            for(String extName: m_extensions.split(',')) {
                stashStringExt.add(String.format( '%1$s\\%2$s\\**', Folders.EXT.m_path, extName))
            }
            MainBuild.stashResource(m_stashNameForExt, stashStringExt.join(', '))

        }

    }

    protected void commandForRunTest(){

        MainBuild.unstashResource(MainBuild.baseFolder())
        MainBuild.unstashResource(getClassName())
        MainBuild.unstashResource(getName())

        final String workPath = s_script.pwd()
        final String resultFolder = MainBuild.getResultFolder()

        if (m_extensions != ''){
            MainBuild.unstashResource(m_stashNameForToolsExt)
            MainBuild.unstashResource(m_stashNameForExt)
            MainBuild.run1C(String.format('ci_cfe --folder %1$s --tools ext --epf build\\epf\\ChangeSafeModeForExtension.epf',
                                            Folders.EXT.m_path),
                            MainBuild.baseFolder())
        }

        String fileNameConfig = 'vanessa.json'
        final String configVanessa = """{
                                            "КаталогФич": "${workPath}\\${Folders.FEATURES.m_path}",
                                            "ДелатьОтчетВФорматеАллюр": "Ложь",
                                            "КаталогOutputAllure": "${workPath}\\\\${resultFolder}",
                                            "ДелатьОтчетВФорматеCucumberJson": "Истина",
                                            "КаталогOutputCucumberJson": "${workPath}\\\\${resultFolder}",
                                            "КаталогиБиблиотек": "${workPath}\\features\\Libraries",
                                            "ЗавершитьРаботуСистемы": "Истина",
                                            "ВыполнитьСценарии": "Истина",
                                            "ТаймаутЗапуска1С": "60",
                                            "СписокТеговИсключение": "Draft"
                                        }
                                    """
        //noinspection GroovyAssignabilityCheck
        MainBuild.writeTextToFile(fileNameConfig, configVanessa)

        ArrayList partOfText = new ArrayList()
        partOfText.add('start --test_manager')
        partOfText.add('--epf vanessa-behavior.epf')
        partOfText.add(String.format('--options "StartFeaturePlayer;VBParams=%1$s\\%2$s"', workPath, fileNameConfig))

        if (m_type==BehaveTestType.THICK){
            partOfText.add('--thick')
            MainBuild.run1C('reg_server')
        }else if (m_type==BehaveTestType.WEB){

            //noinspection GroovyAssignabilityCheck
            MainBuild.startBat(String.format('copy %1$s %2$s || exit 0', m_pathToConf, workPath))
            //noinspection GroovyAssignabilityCheck
            MainBuild.startBat(String.format('MKLINK /D %1$s %2$s\\%3$s || exit 0', m_linkToBase, workPath, MainBuild.baseFolder()))

            ArrayList partOfWebinst = new ArrayList()
            partOfWebinst.add('[' + '"webinst"')
            partOfWebinst.add('"--params"')
            ArrayList partOfApache = new ArrayList()
            partOfApache.add('"-apache22')
            partOfApache.add(String.format('-wsdir %1$s', m_baseName.replace('\\', '\\\\')))
            partOfApache.add(String.format('-dir %1$s', m_webDir.replace('\\', '\\\\')))
            partOfApache.add(String.format('-connstr File=\\\"%1$s\\\";', m_linkToBase.replace('\\', '\\\\')))
            partOfApache.add(String.format('-confpath %1$s"', m_pathToConf.replace('\\', '\\\\')))
            partOfWebinst.add(partOfApache.join(' ') + ']')
            String fileNamePublish = 'webinst.json'
            //noinspection GroovyAssignabilityCheck
            MainBuild.writeTextToFile(fileNamePublish, partOfWebinst.join(', '))
            //noinspection GroovyAssignabilityCheck
            MainBuild.run1C('file --params ' + fileNamePublish)

            //noinspection GroovyAssignabilityCheck
            changeAlias(m_pathToConf, m_baseName)
            //noinspection GroovyAssignabilityCheck
            MainBuild.startBat('start ' + m_commandStartApache)

        }

        //noinspection GroovyAssignabilityCheck
        MainBuild.run1C(partOfText.join(' '), MainBuild.baseFolder())

        final String fileName = 'CucumberJson.json'
        final String newFileName = java.util.UUID.randomUUID().toString() + '.json'
        MainBuild.startBat(String.format('move %1$s\\%2$s %1$s\\%3$s', resultFolder, fileName, newFileName))

        MainBuild.stashResource(getStashNameForResult(), resultFolder + '/**')

    }

    protected void commandAfterTest(){

        MainBuild.startBat('taskkill /IM 1cv8c.exe || exit 0')
        MainBuild.startBat('taskkill /IM 1cv8.exe || exit 0')

        if (m_type==BehaveTestType.WEB){

            //todo найти ключ для запуска браузера без ошибок после принудительного завершения (пока просто очищаю кэш)
            MainBuild.startBat('taskkill /IM chrome.exe /F || exit 0')
            MainBuild.startBat('rd /Q /S "%userprofile%\\AppData\\Local\\Google\\Chrome\\User Data\\Default"')

            //noinspection GroovyAssignabilityCheck
            MainBuild.startBat('taskkill /IM ' + m_pathToApache + ' /F || exit 0')

            //noinspection GroovyAssignabilityCheck
            MainBuild.startBat('rmdir ' + m_linkToBase)
            //noinspection GroovyAssignabilityCheck
            MainBuild.startBat(String.format('copy %1$s\\%2$s %3$s /y || exit 0', s_script.pwd(), m_confName, m_confDir))
            //noinspection GroovyAssignabilityCheck
            MainBuild.startBat('rmdir ' + m_webDir + ' /S /Q')

        }

    }

    //не делать static иначе не работает на jenkins
    private void changeAlias(final String pathToConf, final String oldAlias){

        final String text = MainBuild.getTextFromFile(pathToConf)
        //noinspection GroovyAssignabilityCheck
        MainBuild.writeTextToFile(pathToConf, text.replaceAll(oldAlias, ''))

    }

}

return this
