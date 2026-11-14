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

        Private Shared ReadOnly UTF8_NO_BOM As New UTF8Encoding(False)

        ''' <summary>
        ''' Create an HttpWebRequest sending Authorization: Bearer.
        ''' </summary>
        Private Shared Function makeHttpWebRequest(ByRef serviceURL As Uri, ByVal accessToken As String) As HttpWebRequest
            If String.IsNullOrWhiteSpace(accessToken) Then
                Throw New ArgumentException(OdiseeConstant.ERR_NO_AUTH_INFO)
            End If
            Dim httpWebRequest As HttpWebRequest = CType(WebRequest.Create(serviceURL), HttpWebRequest)
            httpWebRequest.Headers.Set("Authorization", "Bearer " & accessToken.Trim())
            httpWebRequest.AllowAutoRedirect = False
            Return httpWebRequest
        End Function

        ''' <summary>
        ''' Post an XML document to an Odisee server using Bearer authentication.
        ''' </summary>
        Public Shared Function doBearerPost(ByRef xmlDocument As XmlDocument, ByRef serviceURL As Uri, ByVal accessToken As String, Optional ByVal timeout As Integer = 30000) As HttpWebResponse
            Return doBearerPost(xmlDocument.OuterXml, "text/xml; charset=UTF-8", serviceURL, accessToken, timeout)
        End Function

        ''' <summary>
        ''' Post a request body (XML or JSON) to an Odisee server using Bearer authentication.
        ''' Throws on HTTP >= 400 responses.
        ''' </summary>
        Public Shared Function doBearerPost(ByVal body As String, ByVal contentType As String, ByRef serviceURL As Uri, ByVal accessToken As String, Optional ByVal timeout As Integer = 30000) As HttpWebResponse
            Dim httpWebRequest As HttpWebRequest = makeHttpWebRequest(serviceURL, accessToken)
            httpWebRequest.Method = "POST"
            httpWebRequest.ContentType = contentType
            Dim byteBuffer() As Byte = UTF8_NO_BOM.GetBytes(body)
            httpWebRequest.ContentLength = byteBuffer.Length
            If timeout > 0 Then
                httpWebRequest.Timeout = timeout
            End If
            Using stream As Stream = httpWebRequest.GetRequestStream()
                stream.Write(byteBuffer, 0, byteBuffer.Length)
            End Using
            Try
                Dim httpWebResponse As HttpWebResponse = CType(httpWebRequest.GetResponse(), HttpWebResponse)
                If CInt(httpWebResponse.StatusCode) >= 400 Then
                    Dim statusCode As Integer = CInt(httpWebResponse.StatusCode)
                    httpWebResponse.Close()
                    Throw New WebException("HTTP " & statusCode.ToString())
                End If
                Return httpWebResponse
            Catch ex As WebException
                If ex.Response IsNot Nothing Then
                    Dim errorResponse As HttpWebResponse = CType(ex.Response, HttpWebResponse)
                    If CInt(errorResponse.StatusCode) >= 400 Then
                        Throw
                    End If
                End If
                Throw
            End Try
        End Function

        ''' <summary>
        ''' Save a document to disk. Process Odisee server's response and save the returned byte stream to a file.
        ''' </summary>
        Public Shared Sub saveDocument(ByRef xmlDocument As XmlDocument, ByRef httpWebResponse As HttpWebResponse, Optional ByVal filepath As String = Nothing)
            If IsNothing(filepath) Then
                Dim nameNode As XmlNode = xmlDocument.SelectSingleNode(OdiseeConstant.REQUEST_NAME)
                If nameNode IsNot Nothing AndAlso TypeOf nameNode Is XmlElement Then
                    filepath = CType(nameNode, XmlElement).GetAttribute("name")
                End If
            End If
            Dim bufferSize As Integer = 8 * 1024
            Dim byteBuffer(bufferSize - 1) As Byte
            Dim fileStream As FileStream = New FileStream(filepath, FileMode.Create)
            Dim responseStream As Stream = Nothing
            Try
                responseStream = httpWebResponse.GetResponseStream()
                Dim readCount As Integer = responseStream.Read(byteBuffer, 0, bufferSize)
                Dim totalReadCount As Long = 0
                While (readCount > 0)
                    totalReadCount = totalReadCount + readCount
                    fileStream.Write(byteBuffer, 0, readCount)
                    readCount = responseStream.Read(byteBuffer, 0, bufferSize)
                End While
                If httpWebResponse.ContentLength >= 0 AndAlso totalReadCount <> httpWebResponse.ContentLength Then
                    Throw New IOException("Incomplete response body: expected " & httpWebResponse.ContentLength.ToString() & " bytes, got " & totalReadCount.ToString())
                End If
            Finally
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
