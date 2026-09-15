package com.techcell.caixadaloja;

import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

final class EmbeddedApp {
    private static final String DATA =
            "H4sIACuOqWoC/9V963Lb2Hbmfz8FzM4xgAiESEqyZVAgo5attCu+dFnu031Kozq1CWyKsHFhA6BEmmJV8mOm5u8kc8lUTVUmM1WT" +
            "mqk6v/IIfpPzBHmEWWtfgA0QpGi700nqnDaBjX1ba6/Lt9begE4e+omXL6ZUm+RROHhwgj9aSOJrtzXN29++bWEZJT78RDQnmjch" +
            "aUZztzXLx+3jliyOSUTd1k1Ab6dJmrc0L4lzGkO128DPJ65PbwKPttmNFcRBHpCwnXkkpG7Xkq3a4yB3veSGprVu8wmNaNtLwiRV" +
            "ev6mM+o+Puhg3TzIQzo4I8GcaD7RXibvidazj072+YMHJ1m+wF8nTZJ82W6Prp1vxgfjo/HTfrvtkdSH2/EYroP4g/NNt9M97h3D" +
            "XTTLKTx6/PhJ5/gI7sMgps439JA+oR7cxuRm4YhZwO11SmkMrY9HT59g6xTbjg57B128yybET26djtbtTOda7xj+Sa9HxOg+tnqH" +
            "1mHHsjvH5upPl6Nk3s6Cj0F87YyS1KdpG0pWuCrWKPEXy4ik10HsdPpRELcnNLie5E630/lNf0S8D9dpMot954akBhJp9hnPxD3Q" +
            "ZvbHwL32mERBuHCyRZbTqD0LrDaZTkPa5gVW64JeJ1T74UXLykictTOaBuPVaJbnSQyLN53lS+zGCeIJPMnFk6U3SzMYbJoEsEDp" +
            "yvYo/i6r87yZ9P0gm4Zk4Vyngd+HK5CLAIbNHN6gPyW+j+T3DqfzlR0mQO6SCY4DXRlHPeCfhRSb/ZzO8zYJg+tYtGXVE1H7yePp" +
            "vC9GZteSdRqZ5YnWxSLB4pT4wSxzegdYVOcjLrN576w5p5kYMR7DGlIHl5nf3vJ5PO10+myFuTTwAfiNySevjZbqojGhMleTbrHw" +
            "Wkd7IntlgxzgICHNYRbtbEo85F3b7hzSaGVns2p3TKLNvtJXr4dcnpKYhkuFdEYI547TBVnNkjDwNd4JqoFZZx2sSrFy3WPG2k1k" +
            "jpM0Wlb4eU2mTheWW13RkI5zqBvQ0F+v/AQnzZ4NgOR4WXKje1Bj+ZOjzspmUmvZURLThSqRRweFEOxEJptkhU1kPBp7YgAheEwd" +
            "JS9A4XGMZJYz6xHDDFZiHlWq4J82CBSU5MzWzaI4cw7QWHTHaZ/xpFlPxAjryyzG0fjckGo+v46kuFNMq6MSlaeg9VOSwjCqLHdr" +
            "jD0GWa7bF5CkNADpWlh2RsFS++zSB3cClqDGSpy0shSHxw39KzQeMTnlvS8LAjZoa6mNq3Iiy+0LXRP+ddIqZCi9fDPujg6Jv7WH" +
            "FNeDG0oH1p2MQuovE9TWfOHYR0d9YT3jBOU/TG6pv7LzZDoi6VK1YdMkA+cJnWR54H1Y9KEKcOEjzNCnc+eo36zC4ENg5GgD5TvI" +
            "4eFjLocaXtwjjKgiqKPHuGCcBM2+nSTLNXPdV2SyqJpFQL9V3OVpEl8XqjIKE+9DtWqTfVPMAdql2wnMldlGVMDblEz7iDLGwGZn" +
            "Evg+jbnhKQppGAbTLMhWtWko2tBB6gKQrFEu3dNhr3Q47Pqz5G1dO0B/ASpt77yiTfcqw71erOar+AyaXZItgJjim58el75ZuBh0" +
            "tRW/wORDe8zYF5M0TW7Bs82FFADQwnIfRA/cR7Hq45A2Ch1K2dO6Qf48v8WktZhfT8xPzL5QmwNlVhpzONuFjvkgmLTTLZtxK9xk" +
            "uNbsbem5SgO9sgFsJcv7l7jml3sKfb0G0hjIwq4ZXZa4ZIpVUbp+Yaye9GSDBt1UuMCktBGXSOEANmkdBClRdanfz8C6jRdtIWEO" +
            "09z2iOa3IHlNgiBoRmPYvOiCA7zG4QZvCfOQNDXjZkXzwU5TFXX0an1yJWlGqN6HJh8y9o/WfcgRJXJtj8nhUXdckdUmG1CTQiS/" +
            "gN/2IRg0Mso+R2WlSmidXfxESqeU5MahBb7CLJEaDqqJMOGLDWSDytXo7zVBPza2DQPvrDrqQKzOykZqD5a7M+CgZADzQE32JMtJ" +
            "/jWQu/tENV1HWyE3jiUUnF8yMZc369re0GIHX9uVZDX7Sylgh0LtqYdIhoXoX6n/2/msjKNNessapJWxUK3iJnrB9gVZnqRV+L6y" +
            "0ZHtIB+IoZiCFVP+CnPGCERWwuDOOEizvO1NAoiXlA74U7GS7KpptcsHG6NF1htjKNCULxrQXEPTwvMgfmSAkDAeZ7vyCmMf4eZh" +
            "4PmUxhnaM+L/2h6jGD1bj0aPlcc7EWYfAGn2MeLpnkDLT5Q+tPVAFXE32LKUZJMvD2PSBpvJdXYW8bBKbXwMsa335VgKvc8ap54q" +
            "o2l+cPM5q3gv5Cp73uDFEc6TLF8WQdQ4mFO/j5kG5whidrbWGOAwRIZpCo7NcPF+MtpH6CRlqHVQgXAyZ6g4k4IRyDphnu/z1hXn" +
            "VU0VQWCFrvjgSCYOOxb+z+71UEElgH7a+00T5gC5StK8QWXlJHmeQcox1NXsUaDa70P2jLG90Sh6aEcwWK1DC4+EnoH4QmuzrKdZ" +
            "4AyWCGWpuM5XIfgDReqeIhmq5G0KHeqipjiS40Z4yNcGSgICv/EsomngOQAuZiFJ8T4rmbADiuwyfS4brCexMCmXZbdAK4ZChcim" +
            "FKQxuKGVx5rIP0mjlfLkFjdbC1q2BiwENiinfV6DyToIPRP+dZn/nZD57eHtxlimtk6dirwzQNAkSTBfZ5x4s2wpY59eTQoEthaP" +
            "28l4nNHcEYbM82iWYfkO6LwebK1rzp9F1A+IoUSoRzBvc8lh9FYI2GMQUELHjR6udBxbnSBzFIqD8Gnm8Ra8otPdb3dL77p5uNWD" +
            "k32xG3KyLzZ2cGNhcALmWAt8t0Wm09bgZB9ucePES4NpPnhgGKY7WOozGBuEO/Byvf8AdCfLtfPfunq3Zx/YHd06O/9zd0mmwV/Q" +
            "haOfvvhILhan3quff/yRkud/Mfbfv7h413l9cfvhpx9/zPQ9fXSRPw5+1/ugW2AGJs+SiIBZ0D3cxmn7pB0m70n7yPcPDm0AN3RE" +
            "Mgpzs70k0q1pmrwHuPbCb6yvWwjRyDX9duZ9ANnY2qeoakPfuhWB9BCwTtcXFMx8iv33Dg8ODjvdJ8cHnR7MdDrFwq6jFju3dOR0" +
            "ntLOyOv0Hh9R3+v5PY+MnvbG+koy6vTZqxevXT2n3sSjYUi8rPtn10ByyCk6O33x06mrQ3ECFqX7uFt5CsO6PmhFBHJpX9P8eUjx" +
            "8tvFC9/QceamNY5yN6a32os4D+3Xs2hE03NQZxBFne3k6daSLTxwY5aCgnoL3ZJXjv7t25f6yrTAgrixO7iANY6vjdgEK+Nf5CRF" +
            "edY7MEqe+GThMmngZPls0GcEjQrNZ2ms+TjB81kY/o6Ctpl7elvfg24MVv4KdG4Chd1aOXZgmObKirACG4ANZZg2qLNHwd89Ma1R" +
            "6mbuQA5oZHv6u27P6XTg/7oJ3v1lgnuK+ExQIEg3LbDQ7o074HwxxNObuzsd2oHGYirM2P939v61pZboFvzP1k3z7q5jgcrh6KJt" +
            "Vmt7+ehk0NKvoAPPHRhL/ZHu6I9INO1DDyd4HeZ4OcDLa3bZwsufZwnetPQW3Hxz8LSvry69K/CSD0IKysU0w/JHFmheaqVJSF0u" +
            "zCCqyU3mXl5Z4EQymBC7BlmesguwT64ektgjKCZJBhoDDivxXX0CiqNbzPO8C8CLuTGslEV8cNkXMZlmkyR/C2Zh4Y5JmNH+g/Es" +
            "ZkZFY02+B1HIjHLNYcmWfNmXmOhy/K2LkMOAShUcv1rFWk6SWQo61/aD6yAHIoMY/IJSwLP5SgE2ABlgs12Zq1V1vt/lUWjIKWo6" +
            "M3JeCJ7TbUnn2xr809/9l7/WTtAHa0AEGAnwr20kB8wglg5OuENXniIl+JSVC2upl2Njcv4M+4exuZpMXYV/Zr/Q5Z9nNF1c0BCM" +
            "WZKehqGhX1bncAVCBm75OfEmBnUH1EYvdSY2wacsvbhrdzjpe7rDKmZJR4aqLwkJxkYpNqYXgna/QC95Q0L1QV8hvq/IGTjponpR" +
            "BVNPHXNVSlmIkvEiS2oyxpn40W0zY4EdfoQQ6Q1z/YZpZdfux4HbGYJTccCsWMR9RfKJDU7a+GhagBDc7JrZGlY8DpMkNcj+445p" +
            "YgP2hPwGbn8JEwY2SSn8DsQzM5RxeBdMrNfLL5h0s3KYdLkOIYGrCZJtzNlCzIf2BNzWy0LDTTHzea0ccN1CNABXDqHE82gICohz" +
            "LZvIJ+KBYf6zqqhHchQ+c7mSaqnXKf12gXTK+cmpf5+kd3dwR/IZoLSPsgSal/IjEOAFolGQWaT+FiK25NY+jf00CfyhjYHBhVJt" +
            "aBvKpFDSwMzaNE1BSKhqUfj5iNcJRg4GSxNZUXbdPAjY0VtWM4CeoTEMUrbYNlpJCgtWjdwagU9mvDOXzXpu6Dyw1c0h+CPwCxQ1" +
            "j2kMLVGDlwIspQI4GDpYLN3sU5tZw9d4zkbnnfSrNiHvg/TAFExqM/hgl2jf1cX5Fr00QIglEUoBfDrDXBTQ1M+4xgJgZzCSFpO0" +
            "esd17b9ONphr3IlqDd6ejAZ/crI/ajC5yZRzWGmO+Jamsge+lwfYlmeiZbHYxGsx8IsTmOWtwR//w/+FUVi9gTqL20kCHbBU2UDf" +
            "A0RgoGMe2hShGkMEezq4BfZceA1Rj8+NPa04jT1OMz7g04UixXWVBI5Awt4BkYoU1IEgnz2KAQSCz2+g/GUAIhsD4tE9QFGAtHEB" +
            "zu0MYpw3sByILgBrqGZe1aWcDcdAReE05oD8UODnqtKsVtgJxx7KjBfMmvjFiiBkAdwNmgm9zNF3Edd1fUBXCEKUkVn+6xXJPhg0" +
            "lMafGZPMFdaIhjY4khmtI7hnHMEJ8ccwLnMF6OMdAJIDsRetXYPV2AcvVNq8ur1DyYtm0XnKo6tnrBunZ0FU2FQOrED+8AnErhzK" +
            "Dml8nU9w6AwNfcizzW/xUIERW3GFm5VFf4XMeIEBfmZsMgHM1avnPfAkgXEpjxpcmarbD2EJYRqs3qvEB80X+QwddR2e4MLAJO1i" +
            "GYSr6Dc9g/BPxwfrIscG4CJXWVArT2dg6mqKH8RAHWoTQ6RiAcFSUFw86rsMGVyIYA0k/wXYYoOD4d+Dcua/ZzqomygRfQwUgxhm" +
            "8d27Vy9d/QRDywL4sdge1ZhKpMjK2RxaFXVHhVozOieTbvWsIWhud3AylY2y2ag1eB7nKdUgdgOPRNihRfiX+DDPFP61T/anVT0H" +
            "/cfki+yDHcjSsKSwSzA3jVlgsIVlRh6TrjDnkIygvmjMDkYhfYhdn7eRLRLIsiUpLB/e8P4Z71oangQtblL68wxiZJ8l6YCSKUQk" +
            "8BSXCM9ltjSuQi1u38qVAlowgcCmtG1mEGJPiJyYwnQlsdXaPOMMW8sZyyYbJ80D3bxdVKx7AbqgsjP+gI+SJ9fXYBH4WJj8azN6" +
            "3NarBFcy1fg0IIT4T39VOgwuJpID1YHEESbB9hg74dJC0kaPgzvN4I/+9q+0c5GwQGkCyQX4IwdCOcFohMszXKG4D/SKXRcKxea7" +
            "OZXAHkOkBuzYXElhCkKIBbWTmLkXNS+AAMhl3dmMra6rS+bDxMpyrDfUEXEAEi5rYK8qDOHV/unv/vt/hGrIbV4FLNFpDvYaGAfR" +
            "erlAgEJZgzfeLMzlMkHLyrLp5qq/zZcGMVhNIGY2ioLcJdki9gBQge20pylFU/eMjgn0XzB3tJlnfKWRs6hcW+pxIyZ8BlAWQe8j" +
            "WxpyF00n3Kus0Zn0APy0bVtnvofckiDXuJt/Ef8Y5JPn2C2A0+8Fg5nn53Ox+GKwAc1+xcpmG60sbyp91rwOZOfgAxl61bn50ZIZ" +
            "Zzk4Jy8BVcyTzNa5H1Cp4zmHJvJSfaVi45A5GQFOgEMb8xfg0FjWxJV5EwknUvfchlUw/JGlAy4JZJ5EZJ0EnrGns2xinIMUyM6N" +
            "1MpABlj2JbPpHCYCbnl4uQx8J7MD34JVyJiXBIB05VxeQUyJqcQzAh2ZK4sKDVFRv2TWBY00gnFJopFE87iPoRqmbCS3VqYMUlec" +
            "jp+BDgYFYJZeEgpYUSfLtIAGTMdDZKXj5HRLxxyyvoXQnxmhwMB15gLfkXlnE0QvQH6BLbwJb+JNmHY/BLYDLqC+/uiRUhQlPgRG" +
            "UCpxBSdl7iIToR50LTgpbgQ7rdFC4r95PQqcq0GiLrKBt4zvGA5hELOAsVmaFWuz5JifQBHja2UiH+hCiMvvYxbDUf/3+t4c5rSn" +
            "w0UxBZzWd5VwG3uerJXkSU54dIDzyGCBYYnqUAYGNeU0alWysooFaEuaG9atRLdilOHQKAp8PKEfpAlC3r2i1AOfQFiZaWI06o5S" +
            "QYq5Z1QSDUNd+/Q/Mw1CFLXUYUGO9se//F/aOxwRno+jnJ3qJhCuYhHIlBoq66+TmwRfKvn0D5wnmp9oZyKLCeGwFP+1ain16Cjw" +
            "E6dpDASQDWrPDKTQThCdzI7I1PAxJQuy5Qux8qVEMW1C9TzFnj5LPzN1qpmqn0Ixvc2aOabehDSpZkSzXTTTY6pZZH6/mNI6oaaK" +
            "yVW7VST7hXm0IreI7iyQMymF0bAidRYIm/KoFD0LQQkY/IcPo0ePjEhqo8u18e4OOhl07u6gwQDCtSqaR4uvc/FB2FyB9txnaPxk" +
            "aRXLi4OYiNX+5t/zdO8AKCAojEzXq8legOegGP56xF4PG/AspIS0XCGgQz+odcfgHrCuNaiIMtC5B0RWRuHZg2eCjdqehmz79L+T" +
            "/e9f/FTkFkqwty1s8GDxcESDs3uoV4Mb7wNy4z//tfZSUbr3n/4ell6YVr1iEqLSJCDkrzwRdsHWLhLsh2qJxiQtYBFPkmpTiDQ1" +
            "EkLoBfgL8ACdeyFAdVvkQXjzbYFMzEInDXRQilhD8MACTdnk7Z9U4x62BhhHsdsIQ9+WCH1bmsIliUfEpMpAB9drCP+BrT3HsyNG" +
            "T9krstlukeBDS2OlkyQEHXJbHavTadUDg51oFYsPYsCX/7MJhg6+gmBoPYT/fhmClWlns6iIA5OoSVlYjXuVhQ+wIcTaTKGqE6rw" +
            "j8IETDYKvqOfk480VY08T+41xGjKsQMI1f7H32hvtAwif7BG4wBmjjdMJcj00z9moBhFJJeR8AbjPkZEY9B3AYAZKAVl/PQPKAZJ" +
            "jKNoeAaE1MI/EfQVeULEGpzGKrrZEv75GPJYW8IYj8UwKVUjvdwFkTJkBLGHNyMZTmwONGcRYjQF56sOfktDsKHbGq76aymzPgHf" +
            "yRTAHRVXKe1vITKbqJFfRm4ousHVAx4DlglS8QCTdg1BoYg9HpaxR2UdSmeKDhTZtn1dBE+ZT91amy2SrL0pzgG/HUOEXm6yWV5l" +
            "jxIeMgpg6BPuiU86csdIwKEXMbKdorVIUpppN5/+PgS0VkZ2aiyazuJ3eDiIFCiIMzOflykDl1fO50iSkXK0XMRYEoQkroyv7u6W" +
            "K6yTrOEHgTmSChpBRFE8KLEIIIx8gidgkRfPGRLS+ZiAtFcwGdxjTC3c4CaOb8keHbiweC8O/FgMlTrCRlliQg5Ox6pGBI7H91mt" +
            "9fDBKbY/kf1WEdE4mHQrNxksuWHnQIxP0xuaohPOchKB2luVwKjesnzY3BpANeDOyiaaRL9o2F5W0LnoSaQ5OTDm8ECQuIeHJ+5N" +
            "EsxtfuyGRemC80P9TTUAZrhknATCIPuJDSb6NZpELJwmWfbpDzfgTrlJLeC4up+G28HK7lBMCnuLj9DZXF5WD02oviHTr6xLHeR8" +
            "FiXw5C2/wLISzUP5eXmDz/hxSlBIS38uL6+uGFQHwdel52Kb9HhoA3zu/LJzhf5UzA08FT5wXSwf6kksXBjOd37ZvVJ8Emj9+wTA" +
            "vdiGAgIH+hqgF/h/qVgnFkYV1qkBbVeQHHTOWSknVuHZkHX2KrkxTEc8FjwbiuO55QOFcUMRzMBDzrLfBvQW0zClO7v3kAP0qh5v" +
            "GLmDUTUzicOOih0MuKuFRHw0eVcyriSpiIPEfi88Qet5d/cLxUX9Qjq3hjW1EESB/VVcorzRwJ/I6OL07PnFxRvt3Zt3py+LmGLS" +
            "GyjyfrIP9xJi1AHKpmBqHX4KDsnkus+uC3zp870CvhNqQCxYizY2jCnP6wzQfGpJGlwHMQnXArj1IGV9//XXijuQD5tCj3/T8QUQ" +
            "drYpxPhV4wgWfDcEEjBB9mjnaELpXhyobW3dxYEBLgAJskA7GuoXzANpLM/NQDvI2Bk47C2xRLVz/qJ50fczGrJQBrqGnopABqOY" +
            "5zyGVjte24UqEgVNRmPLluJmOwJ24TtwBp/+EdZYmAl5IoLt8POdbggjlWkZmVnmLupcFq9UcQaWPQzZdeEqlQZFckcs/cmIp2s2" +
            "5jExW7lmD+YyacH3doXgVLMvFZFpzKzKZOhZmaXZ0ErJvMoZsoNODfNj5cXsKnwrrFvTEF+QA26wiSWMcCpsZ29+tQavaTyZRerq" +
            "yiQO66smdiIuXQueeO6W4DmgzVhEutvNZ5+5e5Hxzv2hFLfDnxFNcftWNmAAFZCK4uh3CqQe+iytuSmYOmPJAnBmmYynijBKnCAl" +
            "izAhvvsVccjXRQaMCEm9+WaEB/BtkIvgOjbE5PBw4K8Y6qyqISaAumcQ6W6Md4tJgne6powlKxmDSLqq6SC04YAteLBRecBm9SXB" +
            "zk4xS5OqgBv4JTWFrWYhwnd3D0W2ytClU6lt2VCtyIgP9WKnSuW/T/GwxbYlKJig8pIlgj/9AZn8ZeyTmeSGmG8cYMKZ+gVyz8sd" +
            "jMTNlXcLkB/ijLwrTsmb1WNr2FPl4Fpea4SbN1vbDJXxoEGU1DogcbJrB4fYQa7cFqEDNqwciBVB11Jm/0qeMGtJAIj5ELwbRmbN" +
            "IUrK9hqdhyWili3Vle2deyKZKrxg0bcEIuWXZPiHjURYzl9b0L/DH4yr+UbZq0//j4flyDlLP4150J3PfLx9hz+bgm3B9cZ4u1iR" +
            "zw6568iGvf1VA1M5ySVkfVYLKJp8O3CjYXtI3yMlzsJ31LbhK3XIsya83zRu87YUsObTHxIwfRnG3yBg7BjSLgM3wPNd98IoD1ey" +
            "2kCN23Ec1t4DX18Jq8ST6mqkuwGVSmYPyX149BdAonKFd0Ge8l70ex/s1Js3E/+VAEvcX1c31bUYPD3VpMzthDILwwf+7yxMMgz7" +
            "Zc5PbpgrZ5DBjqAllkeQ1RPI+QTD5IBmSg87mXVzs4n8kjMalRPydD59m9zC2Es8JAABGKA+QEkMMjqdVfPZefHaaONpSg37aXGb" +
            "iJfVU51zuxhGnG6vxefP2GOmRq3d8wZsNBjonqRBwQ9GHrJjWC0yvy6fUA272dcVBCfSaPDpv9VCaVW4imyl8KqRTAjiA8reELq7" +
            "i/iLQpbnKrKIexYQQ7inaUoWdpCxX8NDPk9pRjLz0aPyRlqdssS5bF74KyuceTKl6NlwI9w2zV063yyQBWetXy/5eP787LvTV89f" +
            "v3ujph4xoR7iqVYNlD9DV1HPP27OZyEOuO+Ic7kyMhcZ8ZtC3qIdDy6/ROaC6DIb6X9RNpCtz305M6g0hP9+8ZyZ+h0VzGwNngnh" +
            "4pwPMuaFqspRfEVRpqX85/Npa7CnnfoBgwFpPfG0PmAmDj3LO8zQzpk/5VZtO5QSQLaS9xF5zAyP3ESKDDS5sppFb877SVwmGNKQ" +
            "TYSpNmUTab6pw5Ei2eGnP/w8C1BkRpVOY5rX+oN1bzf2ufXAAzs3gZvSKOvgT3kmstxmUdZoi+dMQS5gdcu9sks8SLblvROxorrJ" +
            "sXaKZ9BKK5XW31a7LJzNVe2kszBnmI/Z0AoqFI3wUFvVGZe+SjqIlUIV+xaJtNjULci0wESuWUjRAYY94fYEEdNlfOlKnHnATfON" +
            "x7uF9EAAvUOdLecjtg0C0rStf/Z4U9dhOzebzzkw/1X6u82nDwo7WyTldmNgkWDbeWW2Z3/UQ5ZWZGLaJ3Mii43mhFbhUWlxydju" +
            "5FYqFfZlwNTVAa58ZfKsMemkbBazHFDyiyWQVLxYbKYuv8K/836qHn4UXLcGf/yv/0e+FtUbfA/oPQgnpDjgy5148WoU+8hLayB2" +
            "wqtYnyp2KuPsgACgPCzF3pk6mfIIq7LPgNa0ttfAwiFWs8D8ldrlUCxwnm5/S4fTLjMU5VeeW4NvkchUez73gEeGPQ+xt11sLH4C" +
            "xLgB+db5aXJdWZ0zipCInU3NMucdwyr6Xs7zD/LTFcNh5cXHR/jeI//ehFJ6wkrxyxNK4YAV4jcomHvBcWCGbFDlfVpO9E9hVuh8" +
            "GrmM7TzYMe0MnhsGsUZm8VEMwqNem+V26VkS4aeA5Nc2+M47RDRlCA1OH5cIeSEDZnZTDY15WXNWSufFer2SkovaVOVLYrFaX+pm" +
            "jSRj7e10xmVGaQlwrNRzC8ncylAwIRv5ic82shMf1iguo4JNPKlYwq016zZyOFyvrHR4d3d5xWe6cAeL0lPv6U5j2mJRRiamZJt2" +
            "p+lmIzfnUejqJ0P40cDu4qsTbqtrd1rDwcmPSfphlCQfNHgYg2aDijmZNwHTnbWjAPiRJeO87SWRk4zHsA5ONkX3k00oYDLeyMk+" +
            "tx0fll2jCrPX61vqWSKo8Q63cQfqmumoipJ3Ojva4Ce1TWNRUUhqUVDJ6hWlAkaIu5fi4BT4jWKF9NNCWkWx5O5eGuG1mOV+QU8j" +
            "aaUf20AZS9QWE+H+XtIiJKQoWIfLSl0gaIJpdmWe3oZ57sulH+gW+0Mitc9AgUnlWwF7OhpuXX4qY+1TDoB+vgUH9PjwPAiptIaj" +
            "RU4ztuv3DpDU89iDKC4FJabsygDBMfHrx/gGt6vrfZBuA28Dt9MPTlhj+UZ4sOce9J48Pjah6p54v8kep0l0NiHpGXYG8Ju3yGYj" +
            "wvIFgRXs8Ub4eTZ1vrXpGki5hZ+ICsWXKPZvYt+OsjZFn6VbI0AVBoxc7s0UTpyhCjwj8iy5jXHnLAN8It8+2/INC8GgMBkx/nwL" +
            "F8YlMOTKWmLg7WycTV/+dRn2x2Xwe1Rk4/cr8NVTYk9SOnZ/ePtSPOWbknBv4OhYwRdTd5EPcM9Ogxlr36TALlJ6k3xQuuC9m2uf" +
            "qFHOiC3XDxvvvg/HMLzp4xE19h4dP6NWPVjm+mK3rnpQ7f6T3HKbe9thbrmzjRMhjx6NylcjcSbbmt0Tn2w8D77hLPZq21h4RGDr" +
            "Jy2KgwRbTo3zYzT3dwOVdjhoyPeHtp81FHtI5XFDXrB24lB8qGLHgAqXyotML2qWmrKqC1UaJef+MHbjhxx4BL0Df0SQrnwyZK4c" +
            "vN+xkzRq6ENh8JxTixarTECU37+R4f5qq1RgmHnv91KW20J1lsqCgQP4TfNT/z3B70vg0VlDH1GYPgX+65bI25vVI6bbXpCQMffW" +
            "6RW1tvTEkfzWbgqwvx77gwPNwdD9i39Q44x/9ADEHN+NLoJCu/FTGvd8CqH8Pgv24eqTPJ9mzv7+7e2tfY17loGHn13cl5+IfJ/t" +
            "63vnv7UuT60frGdX4jWF78FFBzAFAjJ7GUTIQQNr7+lFQ/zDWvZ7fMdz0/NZPtlaAS9wG5KyWlemNSbuqS3+hFrwkZ5Op8bZ+Z+D" +
            "YYWe3B9w8U/hyhgTkIiR+4x9yEz2wUrP3SVw7Qd8OfTZqv8DKBU2uACqKX+fW3wcYMbf5H44K16yFx9pkQiAfatl1mev1xsz5RNI" +
            "1VevXddln74cigMr4muc+vqr+Y8ebelGvrttSrhxS9KYHZvCvxOH2Be/CYCHWvOUsF9YQfFmJ9hN+cGAvhyST2ZYsY5O5VX9LZ/p" +
            "+mW/L/OwVINzwrIn/Bsfc743vaYKv8U/yAZRF9XwewowVgzAEm5GEDDEyQ0D4iJdsp6CWD3gGt1fmfAPfg2Wf+IV1JF9Bnaf/yHA" +
            "/w8/P6CfGXAAAA==";

    static String html() {
        try {
            byte[] compressed = Base64.decode(DATA, Base64.DEFAULT);
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
