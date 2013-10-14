package com.zope.s3blobserver

object Answer {
  def apply[T](
    f: (org.mockito.invocation.InvocationOnMock) => T
  ): org.mockito.stubbing.Answer[T] =
    new org.mockito.stubbing.Answer[T]() {
        def answer(invocation:
                       org.mockito.invocation.InvocationOnMock): T =
          f(invocation)
      }
}
