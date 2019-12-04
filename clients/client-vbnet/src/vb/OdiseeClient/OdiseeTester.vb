'
' Odisee(R)
' Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
' Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
'

Option Strict On
Option Explicit On

Imports System.IO
Imports System.Net
Imports System.Xml
Imports System.Environment
Imports System.Threading
Imports System.ComponentModel

''' <summary>
''' Test requests against an Odisee server.
''' </summary>
''' <remarks></remarks>
Public Class OdiseeTester

#Region "Properties"

    ''' <summary>
    ''' Path for generated file.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Private Property savePath As String
        Set(value As String)
            savePathTextBox.Text = value
            My.Settings.odiseeOutputDirectory = value
        End Set
        Get
            Return savePathTextBox.Text
        End Get
    End Property

    ''' <summary>
    ''' Name for generated file.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Property saveFilename As String
        Set(value As String)
            saveFilenameTextBox.Text = value
            My.Settings.odiseeOutputFilename = value
        End Set
        Get
            Return saveFilenameTextBox.Text
        End Get
    End Property

    ''' <summary>
    ''' The "simple HTTP POST" Odisee client.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Private Property odiseeClient As OdiseeClient

    ''' <summary>
    ''' 
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Property odiseeServer As String
        Set(value As String)
            odiseeServerURLTextBox.Text = value
            My.Settings.odiseeServer = value
        End Set
        Get
            Return odiseeServerURLTextBox.Text
        End Get
    End Property

    ''' <summary>
    ''' Generate the Odisee service URL from UI/user input.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    ReadOnly Property odiseeServiceURL As String
        Get
            Return protocolCombobox.Text & "://" & odiseeServer & "/odisee"
        End Get
    End Property

    ''' <summary>
    ''' 
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    ReadOnly Property odiseeGenerateDocumentURI As String
        Get
            Return protocolCombobox.Text & "://" & odiseeServer & odiseeGenerateDocumentURILabel.Text
        End Get
    End Property

    ''' <summary>
    ''' Template, selected by user in a combobox.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Property template As String
        Set(value As String)
            templateComboBox.Text = value
            My.Settings.odiseeTemplate = value
        End Set
        Get
            Return templateComboBox.Text
        End Get
    End Property

    ''' <summary>
    ''' Username entered by user in a textbox.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Property username As String
        Set(value As String)
            usernameTextBox.Text = value
            My.Settings.odiseeUsername = value
        End Set
        Get
            Return usernameTextBox.Text
        End Get
    End Property

    ''' <summary>
    ''' Password entered by user in a textbox.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    ReadOnly Property password As String
        Get
            Return passwordTextBox.Text
        End Get
    End Property

    ''' <summary>
    ''' Odisee XML request, entered into a textbox.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Property odiseeRequestXML As String
        Set(value As String)
            odiseeRequestXMLTextBox.Text = value
            My.Settings.odiseeRequestXML = value
        End Set
        Get
            Return odiseeRequestXMLTextBox.Text
        End Get
    End Property

    ''' <summary>
    ''' Get file path entered by user for merging a document.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    ReadOnly Property mergeDocumentAtEndPath As String
        Get
            Return mergeDocumentTextBox.Text
        End Get
    End Property

    ''' <summary>
    ''' Timeout in seconds.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    ReadOnly Property timeout As Integer
        Get
            Return CInt(timeoutInSecondsTextBox.Text) * 1000
        End Get
    End Property

    ''' <summary>
    ''' Number of requests to send to Odisee server.
    ''' </summary>
    ''' <value></value>
    ''' <returns></returns>
    ''' <remarks></remarks>
    ReadOnly Property requestSendCount As Integer
        Get
            Return CInt(requestSendCountTextBox.Text)
        End Get
    End Property

#End Region

    ''' <summary>
    ''' Constructor.
    ''' </summary>
    ''' <remarks></remarks>
    Public Sub New()
        ' Dieser Aufruf ist für den Designer erforderlich.
        InitializeComponent()
        ' Set title, version string
        Text = "Odisee(R) Client " & My.Application.Info.Version.ToString
        ' Set concurrency
        odiseeNumberOfWorkerThreadsTextBox.Text = CStr(getMaxWorkers())
        ' User settings
        loadUserSettings()
    End Sub

#Region "Event Listener"

    ''' <summary>
    ''' Open the Odisee Server web application.
    ''' </summary>
    ''' <param name="sender"></param>
    ''' <param name="e"></param>
    ''' <remarks></remarks>
    Private Sub odiseeServiceURLLinkLabel_LinkClicked(ByVal sender As Object, ByVal e As LinkLabelLinkClickedEventArgs) Handles odiseeServiceURLLinkLabel.LinkClicked
        Helper.Desktop.openURL(odiseeServiceURL)
    End Sub

#End Region

#Region "User Interface Settings"

    ''' <summary>
    ''' Load user settings.
    ''' </summary>
    ''' <remarks></remarks>
    Private Sub loadUserSettings()
        odiseeServer = My.Settings.odiseeServer
        username = My.Settings.odiseeUsername
        template = My.Settings.odiseeTemplate
        If template = "" Then
            template = "HalloOdisee"
        End If
        savePath = My.Settings.odiseeOutputDirectory
        If savePath = "" Then
            savePath = Environment.GetFolderPath(SpecialFolder.DesktopDirectory)
        End If
        saveFilename = My.Settings.odiseeOutputFilename
        If saveFilename = "" Then
            saveFilename = "OdiseeClientTest.pdf"
        End If
        odiseeRequestXML = My.Settings.odiseeRequestXML
        httpAuthMethodComboBox.SelectedItem = My.Settings.odiseeHTTPAuth
        odiseeSSLCheckBox.Checked = My.Settings.odiseeSSL
        odiseeNumberOfWorkerThreadsTextBox.Text = My.Settings.odiseeNumberOfWorkerThreads
    End Sub

    ''' <summary>
    ''' Save user settings.
    ''' </summary>
    ''' <remarks></remarks>
    Private Sub saveUserSettings()
        My.Settings.odiseeServer = odiseeServer
        My.Settings.odiseeTemplate = template
        My.Settings.odiseeUsername = username
        My.Settings.odiseeOutputDirectory = savePath
        My.Settings.odiseeOutputFilename = saveFilename
        My.Settings.odiseeRequestXML = odiseeRequestXML
        My.Settings.odiseeHTTPAuth = CStr(httpAuthMethodComboBox.SelectedItem)
        My.Settings.odiseeSSL = odiseeSSLCheckBox.Checked
        My.Settings.odiseeNumberOfWorkerThreads = odiseeNumberOfWorkerThreadsTextBox.Text
        'My.Settings.Reset()
        My.Settings.Save()
    End Sub

#End Region

#Region "Odisee Button Events"

    Private Sub toolStripWebsiteLabel_Click(sender As System.Object, e As System.EventArgs) Handles toolStripWebsiteLabel.Click
        System.Diagnostics.Process.Start("http://www.odisee.de")
    End Sub

    ''' <summary>
    ''' Show folder chooser to select destination directory for saving generated documents locally.
    ''' </summary>
    ''' <param name="sender"></param>
    ''' <param name="e"></param>
    ''' <remarks></remarks>
    Private Sub chooseFolderButton_Click(sender As System.Object, e As System.EventArgs) Handles chooseFolderButton.Click
        If folderBrowserDialog.ShowDialog() = DialogResult.OK Then
            savePath = folderBrowserDialog.SelectedPath
            savePathTextBox.Text = savePath
        End If
    End Sub

    ''' <summary>
    ''' Set standard demonstration template, shipped with Odisee Server.
    ''' </summary>
    ''' <param name="sender"></param>
    ''' <param name="e"></param>
    ''' <remarks></remarks>
    Private Sub odiseeTemplatesLinkLabel_LinkClicked(sender As System.Object, e As System.Windows.Forms.LinkLabelLinkClickedEventArgs) Handles odiseeTemplatesLinkLabel.LinkClicked
        templateComboBox.Text = "HalloOdisee"
    End Sub

    ''' <summary>
    ''' Generate an Odisee request XML document.
    ''' </summary>
    ''' <param name="sender"></param>
    ''' <param name="e"></param>
    ''' <remarks></remarks>
    Private Sub makeOdiseeRequestButton_Click(ByVal sender As Object, ByVal e As EventArgs) Handles makeOdiseeRequestButton.Click
        ' Create Odisee client through factory
        odiseeClient = OdiseeClient.createClient()
        ' Create a first request for a certain template
        Dim request1 As XmlElement = odiseeClient.createRequest(template)
        ' Set some values using the fluent API...
        odiseeClient.
            setUserfield("Hallo", "Welt von " & My.User.Name).
            setTableCellValue("Tabelle1", "A4", "Dieser Text stammt vom OdiseeClient/VB.NET!")
        ' Merge document?
        If mergeDocumentCheckBox.Checked Then
            odiseeClient.mergeDocumentAtEnd(mergeDocumentAtEndPath)
        End If
        ' Show request XML in textbox
        odiseeRequestXMLTextBox.Text = Helper.Xml.prettyPrint(odiseeClient.xmlDoc)
    End Sub

    ''' <summary>
    ''' Send request to Odisee server.
    ''' </summary>
    ''' <param name="sender"></param>
    ''' <param name="e"></param>
    ''' <remarks></remarks>
    Private Sub sendOdiseeRequestButton_Click(ByVal sender As Object, ByVal e As EventArgs) Handles sendOdiseeRequestButton.Click
        toolStripProgressBar.Style = ProgressBarStyle.Continuous
        toolStripProgressBar.Value = 10
        ' If there is no XML generated by the user, generate it
        If odiseeRequestXML.Length = 0 Then
            makeOdiseeRequestButton_Click(Nothing, Nothing)
        End If
        If odiseeServer = "" Or username = "" Or password = "" Then
            MsgBox("Bitte geben Sie einen Server, Benutzername und Passwort ein!")
        Else
            ' Save user input
            toolStripStatusLabel.Text = "Saving settings..."
            saveUserSettings()
            toolStripProgressBar.Value = 20
            ' Clear log textbox
            logTextBox.Clear()
            Try
                toolStripStatusLabel.Text = "Sending request..."
                toolStripProgressBar.Value = 50
                '
                Dim maxWorkers As Integer = getMaxWorkers()
                Dim _availWorkerThreads As Integer
                Dim _completionPortThreads As Integer
                ThreadPool.SetMaxThreads(requestSendCount, requestSendCount * 2)
                ThreadPool.GetAvailableThreads(_availWorkerThreads, _completionPortThreads)
                logTextBox.AppendText(String.Format("{0} Odisee workers, available threads {1}, async I/O: {2}" & vbLf, maxWorkers, _availWorkerThreads, _completionPortThreads))
                ' Set maximum connection limit (default is 2)
                ' http://stackoverflow.com/questions/866350/how-can-i-programmatically-remove-the-2-connection-limit-in-webclient
                ServicePointManager.DefaultConnectionLimit = getMaxWorkers()
                logTextBox.AppendText(String.Format("Maximum connection limit {0}" & vbLf, System.Net.ServicePointManager.DefaultConnectionLimit))
                ' Distribute requestes across all workers
                Dim odiseeWorkerStates As New List(Of MyState)
                For i = 1 To maxWorkers
                    odiseeWorkerStates.Add(New MyState("OdiseeWorker" & i, makeOdiseeClient(), timeout, CStr(httpAuthMethodComboBox.SelectedItem), savePath, saveFilename))
                Next
                For requestNumber As Integer = 1 To requestSendCount
                    ' Send request through worker
                    ThreadPool.QueueUserWorkItem(New WaitCallback(AddressOf sendRequest), odiseeWorkerStates.Item(requestNumber Mod maxWorkers))
                Next
                ' Update progress bar
                toolStripStatusLabel.Text = "Success!"
                toolStripProgressBar.Value = 100
            Catch ex As Exception
                logTextBox.AppendText(ex.ToString())
                logTextBox.ScrollToCaret()
                toolStripStatusLabel.Text = "Failed."
            Finally
                toolStripProgressBar.Value = 0
            End Try
        End If
    End Sub

    ''' <summary>
    ''' Make an Odisee client instance.
    ''' </summary>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Private Function makeOdiseeClient() As OdiseeClient
        ' Create Odisee client through factory using a service URL and username/password for HTTP BASIC authentication
        odiseeClient = OdiseeClient.createClient(odiseeGenerateDocumentURI, username, password)
        ' Create a first request for a certain template
        odiseeClient.createRequest(template)
        ' Parse XML from textbox
        odiseeClient.xmlDoc.LoadXml(odiseeRequestXML)
        Return odiseeClient
    End Function

    ''' <summary>
    ''' Calculate maximum number of worker threads.
    ''' </summary>
    ''' <returns></returns>
    ''' <remarks></remarks>
    Private Function getMaxWorkers() As Integer
        Dim procCount As Integer = Environment.ProcessorCount * 2
        Dim concurrentCount As Integer = CInt(odiseeNumberOfWorkerThreadsTextBox.Text)
        Return Math.Max(procCount, concurrentCount)
    End Function

    ' http://msdn.microsoft.com/query/dev10.query?appId=Dev10IDEF1
    Delegate Sub SetTextCallback([text] As String)

    Private Sub AppendText(ByVal [text] As String)
        ' InvokeRequired required compares the thread ID of the
        ' calling thread to the thread ID of the creating thread.
        ' If these threads are different, it returns true.
        If Me.logTextBox.InvokeRequired Then
            Dim d As New SetTextCallback(AddressOf AppendText)
            Me.Invoke(d, New Object() {[text]})
        Else
            Me.logTextBox.AppendText([text])
        End If
    End Sub

    ''' <summary>
    ''' 
    ''' </summary>
    ''' <remarks></remarks>
    Private Class MyState

        Public ident As String

        Public odiseeClient As OdiseeClient
        Public httpAuthMethod As String
        Public timeout As Integer

        Public requestNumber As Integer

        Public savePath As String
        Public saveFilename As String

        Public Sub New(ident As String, odiseeClient As OdiseeClient, timeout As Integer, httpAuthMethod As String, savePath As String, saveFilename As String)
            Me.ident = ident
            Me.odiseeClient = odiseeClient
            Me.timeout = timeout
            Me.httpAuthMethod = httpAuthMethod
            Me.savePath = savePath & "\" & ident
            ' Create destination directory for worker
            Directory.CreateDirectory(Me.savePath)
            Me.saveFilename = saveFilename
            Me.requestNumber = 0
        End Sub

    End Class

    ''' <summary>
    ''' Send an Odisee request.
    ''' </summary>
    ''' <param name="state"></param>
    ''' <remarks></remarks>
    Private Sub sendRequest(state As Object)
        Dim myState As MyState = CType(state, MyState)
        Dim stopWatch As Stopwatch = New Stopwatch()
        Dim webResponse As HttpWebResponse
        SyncLock myState
            stopWatch.Start()
            Try
                myState.requestNumber = myState.requestNumber + 1
                ' Process request
                If myState.timeout > 0 Then
                    webResponse = myState.odiseeClient.process(myState.timeout, myState.httpAuthMethod)
                Else
                    webResponse = myState.odiseeClient.process(0, myState.httpAuthMethod)
                End If
                Dim contentLength As Long
                If Not IsNothing(webResponse) Then
                    ' Save document
                    contentLength = webResponse.ContentLength
                    If contentLength > 0 Then
                        Dim fullPath As String = myState.savePath & "\" & CStr(myState.requestNumber) & "_" & myState.saveFilename
                        Helper.HttpPost.saveDocument(myState.odiseeClient.xmlDoc, webResponse, fullPath)
                    Else
                        Me.AppendText(String.Format("{0}: {1} sorry, got no result for request #{2}" & vbLf, Date.Now, myState.ident, myState.requestNumber))
                    End If
                    ' Close response
                    webResponse.Close()
                Else
                    Me.AppendText(String.Format("{0}: {1} sorry, got invalid response for request #{2}" & vbLf, Date.Now, myState.ident, myState.requestNumber))
                End If
            Catch ex As WebException
                If Not IsNothing(ex.Response) Then
                    Dim httpWebResponse As HttpWebResponse = CType(ex.Response, HttpWebResponse)
                    Me.AppendText(String.Format("{0}: {1} got HTTP error {3} for job {2}" & vbLf, Date.Now, myState.ident, myState.requestNumber, httpWebResponse.StatusCode))
                Else
                    Me.AppendText(String.Format("{0}: {1} job {2} failed with message: {3}" & vbLf, Date.Now, myState.ident, myState.requestNumber, ex.Message))
                End If
            Finally
                stopWatch.Stop()
                Me.AppendText(String.Format("{0}: {1} finished job #{2} in {3} ms" & vbLf, Date.Now, myState.ident, myState.requestNumber, stopWatch.ElapsedMilliseconds))
            End Try
        End SyncLock
    End Sub

    ''' <summary>
    ''' Open document (e.g. PDF with Adobe Reader).
    ''' </summary>
    ''' <param name="fullPath"></param>
    ''' <remarks></remarks>
    Private Sub openDocument(fullPath As String)
        If openDocumentCheckbox.Checked Then
            Try
                ' Save document to disk
                Dim process As Process = System.Diagnostics.Process.Start(fullPath)
                toolStripStatusLabel.Text = "Opening generated document..."
            Catch ex As Exception
                MsgBox("Cannot open document " & fullPath)
            End Try
        End If
    End Sub

#End Region

End Class
