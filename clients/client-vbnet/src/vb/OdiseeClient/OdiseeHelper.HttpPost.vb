'
' Odisee(R)
' Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
' Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
'

Option Strict On
Option Explicit On

Imports System.Net
Imports System.IO
Imports System.Text
Imports System.Xml

Namespace Helper

    ''' <summary>
    ''' Helper for HTTP POSTs to Odisee servers.
    ''' </summary>
    ''' <remarks></remarks>
    Public Class HttpPost

        ''' <summary>
        ''' Did we already initialize the HTTP digest authentication process?
        ''' </summary>
        ''' <remarks></remarks>
        Private Shared initializedHttpDigestAuth As Boolean = False

        ''' <summary>
        ''' Create a HttpWebRequest object which can be used with Odisee server.
        ''' </summary>
        ''' <param name="serviceURL"></param>
        ''' <param name="username"></param>
        ''' <param name="password"></param>
        ''' <returns></returns>
        ''' <remarks></remarks>
        Private Shared Function makeHttpWebRequest(ByRef serviceURL As Uri, Optional ByVal username As String = Nothing, Optional ByVal password As String = Nothing) As HttpWebRequest
            ' Create instance of WebRequest
            Dim httpWebRequest As HttpWebRequest = CType(httpWebRequest.Create(serviceURL), Net.HttpWebRequest)
            ' Authentication!?
            Dim cc As CredentialCache = New CredentialCache()
            If Not IsNothing(username) And Not IsNothing(password) Then
                ' HTTP Digest Authentication
                cc.Add(serviceURL, "Digest", New NetworkCredential(username, password))
                httpWebRequest.Credentials = cc
                httpWebRequest.PreAuthenticate = True
            End If
            Return httpWebRequest
        End Function

        ''' <summary>
        ''' 
        ''' </summary>
        ''' <param name="xmlDocument"></param>
        ''' <param name="serviceURL"></param>
        ''' <param name="username"></param>
        ''' <param name="password"></param>
        ''' <returns></returns>
        ''' <remarks></remarks>
        Public Shared Function doBasicAuthPost(ByRef xmlDocument As XmlDocument, ByRef serviceURL As Uri, Optional ByVal username As String = Nothing, Optional ByVal password As String = Nothing, Optional ByVal timeout As Integer = 30000) As HttpWebResponse
            Dim trycount As Integer = 0
            Dim httpWebResponse As HttpWebResponse = Nothing
            ' Create instance of WebRequest
            Dim httpWebRequest As HttpWebRequest = makeHttpWebRequest(serviceURL, username, password)
            httpWebRequest.Method = "POST"
            httpWebRequest.ContentType = "text/xml"
            ' Authentication?
            If Not IsNothing(username) And Not IsNothing(password) Then
                ' BASIC
                Dim authInfo As String = username & ":" & password
                authInfo = Convert.ToBase64String(Encoding.Default.GetBytes(authInfo))
                httpWebRequest.Headers.Set("Authorization", "Basic " & authInfo)
            End If
            ' Create byte buffer from XML string
            Dim byteBuffer() As Byte = Encoding.UTF8.GetBytes(xmlDocument.OuterXml)
            httpWebRequest.ContentLength = byteBuffer.Length()
            ' Send request
            Try
                ' Timeout
                httpWebRequest.Timeout = timeout
                '
                Dim stream As Stream = httpWebRequest.GetRequestStream()
                stream.Write(byteBuffer, 0, byteBuffer.Length)
                stream.Close()
                stream.Dispose()
                ' Return response
                httpWebResponse = CType(httpWebRequest.GetResponse(), HttpWebResponse)
            Catch ex As WebException
                If Not IsNothing(ex.Response) Then
                    ' Handle HTTP error
                    httpWebResponse = CType(ex.Response, HttpWebResponse)
                    ' HTTP 401: Not authorized, check username/password if any
                    If httpWebResponse.StatusCode = HttpStatusCode.Unauthorized Then
                        Throw ex
                    End If
                    ' HTTP 404: Not found, maybe Odisee Server URL or XML request is empty or corrupt
                    If httpWebResponse.StatusCode = HttpStatusCode.NotFound Then
                        Throw ex
                    End If
                Else
                    Throw ex
                End If
            End Try
            Return httpWebResponse
        End Function

        ''' <summary>
        ''' Initialize HTTP DIGEST authentication.
        ''' </summary>
        ''' <param name="serviceURL"></param>
        ''' <param name="username"></param>
        ''' <param name="password"></param>
        ''' <returns></returns>
        ''' <remarks></remarks>
        Private Shared Function initHttpDigestAuth(ByRef serviceURL As Uri, Optional ByVal username As String = Nothing, Optional ByVal password As String = Nothing) As HttpWebRequest
            Dim httpWebRequest As HttpWebRequest = Nothing
            If Not initializedHttpDigestAuth Then
                ' Create instance of WebRequest
                httpWebRequest = makeHttpWebRequest(serviceURL, username, password)
                ' Make first request because we will receive HTTP 505
                ' Result of first request is a HTTP 401 Not authorized, this is due to HTTP digest authentication
                httpWebRequest.Method = "GET"
                Try
                    Dim firstResponse As WebResponse = httpWebRequest.GetResponse()
                    firstResponse.Close()
                    initializedHttpDigestAuth = True
                Catch ex As Exception
                    ' HTTP 401 Not authorized, ok in first step of Digest authentication
                End Try
            End If
            Return httpWebRequest
        End Function

        ''' <summary>
        ''' Post a XML request to Odisee server using HTTP digest authentication.
        ''' </summary>
        ''' <param name="xmlDocument"></param>
        ''' <param name="serviceURL"></param>
        ''' <param name="username"></param>
        ''' <param name="password"></param>
        ''' <param name="timeout">In Milliseconds, default is 30 000 = 30 seconds.</param>
        ''' <returns></returns>
        ''' <remarks></remarks>
        Public Shared Function doDigestAuthPost(ByRef xmlDocument As XmlDocument, ByRef serviceURL As Uri, Optional ByVal username As String = Nothing, Optional ByVal password As String = Nothing, Optional ByVal timeout As Integer = 30000) As HttpWebResponse
            ' Init for HTTP DIGEST
            Dim httpWebRequest As HttpWebRequest = initHttpDigestAuth(serviceURL, username, password)
            Dim trycount As Integer = 0
            Dim httpWebResponse As HttpWebResponse = Nothing
            While trycount < 3 And IsNothing(httpWebResponse)
                ' Create new instance of WebRequest
                httpWebRequest = makeHttpWebRequest(serviceURL, username, password)
                httpWebRequest.Method = "POST"
                httpWebRequest.ContentType = "text/xml"
                ' Create byte buffer from XML string
                Dim byteBuffer() As Byte = Encoding.UTF8.GetBytes(xmlDocument.OuterXml)
                ' Send request
                Try
                    ' Set content length
                    httpWebRequest.ContentLength = byteBuffer.Length()
                    ' Timeout
                    httpWebRequest.Timeout = timeout
                    ' Write to request stream
                    Dim stream As Stream = httpWebRequest.GetRequestStream()
                    stream.Write(byteBuffer, 0, byteBuffer.Length)
                    ' Do not: stream.Close() and stream.Dispose(), subsequent requests will die at .GetRequestStream()
                    ' Return response
                    httpWebResponse = CType(httpWebRequest.GetResponse(), HttpWebResponse)
                Catch ex As WebException
                    If Not IsNothing(ex.Response) Then
                        ' Handle HTTP error
                        httpWebResponse = CType(ex.Response, HttpWebResponse)
                        ' HTTP 401: Not authorized, check username/password if any
                        If httpWebResponse.StatusCode = HttpStatusCode.Unauthorized Then
                            Throw ex
                        End If
                        ' HTTP 404: Not found, maybe Odisee Server URL or XML request is empty or corrupt
                        If httpWebResponse.StatusCode = HttpStatusCode.NotFound Then
                            Throw ex
                        End If
                        ' HTTP 505
                        If httpWebResponse.StatusCode = HttpStatusCode.HttpVersionNotSupported Then
                            ' Init for HTTP DIGEST
                            httpWebRequest = initHttpDigestAuth(serviceURL, username, password)
                            trycount = trycount - 1
                        End If
                    Else
                        Throw ex
                    End If
                End Try
                ' Next try?
                trycount = trycount + 1
            End While
            Return httpWebResponse
        End Function

        ''' <summary>
        ''' Save a document to disk. Process Odisee server's response and save the returned byte stream to a file.
        ''' </summary>
        ''' <param name="xmlDocument"></param>
        ''' <param name="httpWebResponse"></param>
        ''' <param name="filepath"></param>
        ''' <remarks></remarks>
        Public Shared Sub saveDocument(ByRef xmlDocument As XmlDocument, ByRef httpWebResponse As HttpWebResponse, Optional ByVal filepath As String = Nothing)
            ' Set filepath from request name
            If IsNothing(filepath) Then
                filepath = xmlDocument.SelectSingleNode(OdiseeConstant.REQUEST_NAME).InnerText
            End If
            ' Get response code
            Dim status As String = httpWebResponse.StatusDescription.ToString()
            ' Open file handle
            Dim fileStream As FileStream = New FileStream(filepath, FileMode.Create)
            ' Buffer for reading response stream
            Dim bufferSize As Integer = 8 * 1024
            Dim byteBuffer(bufferSize) As Byte
            Dim responseStream As Stream = Nothing
            Try
                ' Get response stream
                responseStream = httpWebResponse.GetResponseStream()
                ' Read bytes and write them to file
                Dim readCount As Integer = responseStream.Read(byteBuffer, 0, bufferSize)
                Dim totalReadCount As Long
                While (readCount > 0)
                    fileStream.Write(byteBuffer, 0, readCount)
                    readCount = responseStream.Read(byteBuffer, 0, bufferSize)
                    totalReadCount = totalReadCount + readCount
                End While
                ' Compare read byte count with content-length header
                If totalReadCount <> httpWebResponse.ContentLength Then
                Else
                    ' Error handling
                End If
            Catch ex As Exception
                Throw ex
            Finally
                ' Close streams
                If Not IsNothing(responseStream) Then
                    responseStream.Close()
                    responseStream.Dispose()
                End If
                If Not IsNothing(fileStream) Then
                    fileStream.Close()
                    fileStream.Dispose()
                End If
            End Try
        End Sub

    End Class

End Namespace
