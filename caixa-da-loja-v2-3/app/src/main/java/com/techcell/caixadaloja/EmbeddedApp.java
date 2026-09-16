package com.techcell.caixadaloja;

import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

final class EmbeddedApp {
    private static final String DATA =
            "H4sIANP8qWoC/9V9W3PjSJbee/8KNGumAFggRFKqGymQy5KoLs2qpLKk6u5ZjaIDBJIiunBhA6BENcWIndiwd1/84Nn1LcIR67Ej" +
            "vGFHzNO++dH6J/ML9ifsOXkBEiBIsap62t65FIG858lz+c7JRGrvSzdy0rsJUcZp4He/2MMfxbfDa6s2Seuvz2qYRmwXfgKS2ooz" +
            "tuOEpFZtmo7qL2siObQDYtVuPHI7ieK0pjhRmJIQit16bjq2XHLjOaROXwwv9FLP9uuJY/vEahqiVn3kpZYT3ZC41Gw6JgGpO5Ef" +
            "xVLLTxrD5vOdBpZNvdQn3X3bm9mKayvH0fe20jKf722zjC/2kvQOf9txFKXzen143X4y2hk9G73q1OuOHbvwOhrBsxd+aD9pNpov" +
            "Wy/hLZimBLKeP3/RePkM3n0vJO0nZJe8IA68hvbNXZuPAl6vY0JCqP1y+OoF1o6x7nC3tdPEt2Rsu9Ftu6E0G5OZ0noJ/8TXQ1tr" +
            "Pjdau8ZuwzAbL/XFv5oPo1k98X70wuv2MIpdEtchZYGrYgwj924e2PG1F7YbncAL62PiXY/TdrPR+GVnaDsfruNoGrrtGzvWcJJ6" +
            "h9KMv8Pc9M4IqFcf2YHn37WTuyQlQX3qGXV7MvFJnSUYtXNyHRHl/VHNSOwwqSck9kaL4TRNoxAWbzJN59hM2wvHkJPynLkzjRPo" +
            "bBJ5sEDxwnQI/s6L47wZd1wvmfj2Xfs69twOPAFfeNBt0mYVOhPbdXH6rd3JbGH6EUx3ThmnDU1pz1pAPwNnrHdSMkvrtu9dh7wu" +
            "LR7x0i+eT2Yd3jN9FqRT7GkaKU1M4iSObdebJu3WDiaV6YjLrD86akZpykaUxrCGpI3LzF5v2TheNRodusKMG1gH7EVng1eGc3nR" +
            "KFPpi3EzW3ilobwQrdJOdrATn6QwinoysR2kXd1s7JJgYSbTYnOUo/WO1FarhVSe2CHx59LU6UQYddpN4NUk8j1XYY2gGOhl0sGq" +
            "ZCvXfElJu2qaoygO5gV6XtuTdhOWW15Rn4xSKOsR310u/AIHTfO6MOVwnlOjuVMi+YtnjYVJudYwgygkdzJHPtvJmGCjadJBFshk" +
            "j4Yjh3fAGY+Ko6AFCDz2EU1Tqj1CGMGCj6M4K/inDgwFKSnVddMgTNo7qCyao7hDaVItJ7yH5WXm/ShsbDhrNr6GmHEjG1ZDnlQa" +
            "g9RP7Bi6kXm5WSLsS+Dlsn4BToo94K47w0wIaGqXPrpgTkATlEiJg5aWYvdlRfvSHJ9RPmWtz7MJrJDWXBoX+UDm6xe6xPzLUytM" +
            "Q2rlyag53LXdtS3EuB5MUbZh3e2hT9x5hNKa3rXNZ886XHuGEfK/H90Sd2Gm0WRox3NZh02iBIwnNJKknvPhrgNFgAo/wghdMms/" +
            "61SLMNgQ6DlYMfMN+HD3OeNDBR8eYUYUEZTRl7hgbAqKeTuO5kvquiPxZFY0CWD+RvaWxlF4nYnK0I+cD8WiVfpNUgeol27HMFaq" +
            "G1EAb2N70kGUMQIyt8ee65KQKZ4skfi+N0m8ZFEahiQNDZydB5w1TIV52m3lBoc+fxS/LUsHyC9ApfWNF6TpUWF41IqVbBUbQbVJ" +
            "MjkQk2zzq5e5beYmBk1twS5Q/lCeU/KFdhxHt2DZZpwLAGhhugusB+YjW/WRTyqZDrnsVVkhf5zdotyaja/Fx8dHn4nNjjQqhRqc" +
            "9UxHbRAMut3MqzEtXKW4lvRtbrlyBb0wAWxF88eXuGSXW9L8WhVToyALm6bzMvgjFayC0HUyZfWiJSpUyKZEBcqllbhEMAeQSWkg" +
            "SAmKS/39FLTb6K7OOaxNJbc+JOktcF4VI/A5ozKsXnROAVZid4W1hHGIOVXjZknyQU8TGXW0Sm0yIalGqM6HKhsycp8t25BnxBZr" +
            "+9LefdYcFXi1SgeUuBCnn8FvcxcUmj1MPkZkhUgojU3sREwmxE61XQNshZ4jNexU4W7CJyvICpErzb9VBf1o3yZ0vLHoyB3RMgsT" +
            "Z7sz35wAOzkBqAWq0idJaqefA7mbL2TV9Wwt5Ma+uICzR8rm4mVZ2itqbGBrm2Ja1fZSMNguF3viIJKhLvpnyv96Okv9KOPWvARp" +
            "hS9UKrhqvqD7vCSN4iJ8X5hoyDbgD8RQVMCyIX+GOqMTRFJC5+2RFydp3Rl74C9JDbBcvpL0qWq184yV3iJtjRIU5pTeVaC5iqqZ" +
            "5UH8SAGhTWmcbEor9H24mYeOZxMSJqjPbPfnthhZ78myN/pSyt5oYuYOTM18iXi6xdHyC6kNZdlRRdwNuiy2k/GnuzFxhc5kMjsN" +
            "mFslV34Jvq3z6VgKrc8SpV5JvSmud/Mxq/go5MpbXmHFEc7bSTrPnKiRNyNuByMN7Wfgs9O1RgeHIjIMUzBshov3rVZ/hkZSuFo7" +
            "BQgnYoaSMckIgaTj6vkxa10wXsVQEThWaIp3nonAYcPA/5qtFgqoANCvWr+swhzAV1GcVoisGCSLMwg+hrKKOfRk/b1L8yjZK5Wi" +
            "g3oEndUytHBs39EQXyh1GvXUM5xBA6E0FNf4LAS/I3HdK5yGzHmrXIcyq0mG5GUlPGRrAymeDb/hNCCx57QBXEx9O8b3JCfCBiiy" +
            "SeU5r7AcxMKgXJLcwlzRFcpYNibAjd4NKWQrPP4klFbMgltMbd2RvDZgIdBBKemwEpTXgekp8y/z/K85z693b1f6MqV1ahT4nQKC" +
            "Kk6C8bZHkTNN5sL3aZW4gGNrnl2PRqOEpG2uyByHJAmmb4DOy87WsuT8WUBcz9YkD/UZjFufMxi9FgK2KAQU0HGlhcsNx1ojSA2F" +
            "ZCBckjisBivYbm7Xm7l1Xd3d4ou9bb4bsrfNN3ZwY6G7B+pY8VyrZk8mte7eNrzixokTe5O0+4Wm6VZ3rk6hb2Buz0nVzhcgO0mq" +
            "HH5tqc2WuWM2VGP/8Ctrbk+8Pyd3bbV/9KN9ftd33v7wzTfEHvz5yP3+6PyicXJ+++Hbb75J1C11eJ4+937d+qAaoAbGB1Fgg1pQ" +
            "HdzGqbt23Y++t+vPXHdn1wRwQ4Z2QmBsphMFqjGJo+8Brh25leVVAyGafU1eT50PwBtr2+RFTWhbNQLgHhu00/U5ATUfY/ut3Z2d" +
            "3UbzxcudRgtGOplgYrMtJ7dvybDdeEUaQ6fRev6MuE7LbTn28FVrpC4EofoHb49OLDUlztghvm87SfPPrmHKPpvRfv/o276lQnIE" +
            "GqX5vFnIhW4tF6QiAL40r0k68Ak+vr47cjUVR64boyC1QnKrHIWpb55MgyGJD0GcgRVVupOnGnO68ECNaQwC6typhnhqq6/PjtWF" +
            "boAGsUKrew5rHF5roQ5axj1P7Rj5WW1AL2nk2ncW5QY2LZd2emCjUiHpNA4VFwd4OPX9XxOQNn1Lratb0IxG09+CzI0hsVlKxwY0" +
            "XV8YARagHdCuNN0EcXYI2LsXujGMrcTqig61ZEu9aLbajQb8T9XBuh9HuKeIeXwGfOq6ARraurG6jC4az725v1ehHkgshsK07d+Y" +
            "29eGnKIa8F9T1fX7+4YBIoe987pJqe7l071uTb2CBhyrq83Vp2pbfWoHkw60sIfPfoqPXXy8po81fPxhGuFLTa3By5OdVx11celc" +
            "gZX8wicgXFQyDHdogOTFRhz5xGLMDKwa3STW5ZUBRiSBAdFn4OUJfQD9ZKm+HTo2skmUgMSAwYpcSx2D4KgGtTwXHlgxK4SVMmwX" +
            "TPZ5aE+ScZSegVq4s0a2n5DOF6NpSJWKQqu8A1ZItHzNYcnmbNnnGOhqu2sXIYUOpSLYf7GIMR9H0xhkru56114Kk/RCsAtSAovm" +
            "SwlYAXiAjnahLxbF8b5JA18TQ1RUquQcHyynVRPGt9b9p7//D79T9tAGKzAJUBJgX+s4HVCDmNrdYwZdysWZYC5N59pSzfvG4Pw+" +
            "tg99MzGZWBL99E4myz9MSXx3TnxQZlHc931NvSyO4QqYDMzywHbGGrG6xEQrtc83wSc0vLhpczjoR5rDIno+jwRFX0zEG2k52+iO" +
            "D9J9hFbyxvbljI40+Y7EZ2Cks+JZEQw9NfRFzmU+csZREpV4jBHxR6tOlQU2+CO4SKfU9Gu6kVxbP3atRg+MShvUimFbb+10bIKR" +
            "1n7UDUAIVnJNdQ1NHvlRFGv29vOGrmMFmmP/El5/ChUGOklKfAPsmWhSP6wJytbL6eeUu2k6DDpfB9+GpzFOW5vRhZj1zDGYreNM" +
            "wnU+8lkpHXDdHa8AphxciUHQAwHEseZVRA7P0PQ/qYg6dorMp88XQizV8kxf3+E8xfjE0N9F8f09vNnpFFDajyIFquf8wxHgOaJR" +
            "4Fmc/S14bNGt2Q/dOPLcnomOwblUrGdq0qCQ00DNmiSOgUmIrFHY+YiTCD0HjYaJjCC5ru4E9OgtLelBy1AZOslrrOstnwp1VrXU" +
            "GIJNprTT59VyrqnMsVX1HtgjsAsEJY9KDMlRgxMDLCUcOGgqaCxV7xCTasMTPGejskY6RZ2QdoB7YAg6MSl8MHO0b6n8fIuaKyDE" +
            "kgilAD7tYywK5tRJmMQCYKcwkmSDNFovy9J/Ha1Q17gTVeue7Q27v9jbHlao3GjCKCxVR3xLYtEC28sDbMsi0SKZb+LVKPjFAUzT" +
            "WveP//Z/QS+0XFcexe04ggZoqKyrbgEi0NAw90yCUI0igi0VzALN51aDl2Njo7kFo7HF5owZbLiQJJmufIJD4LALmKTEBWUgyEaP" +
            "bACO4OAG0o89YNkQEI/qAIoCpI0LcGgm4OOcwnIgugCsIat5WZZS2h0FFZnRmAHyQ4afyUKzWGAjDHtII76j2sTNVgQhC+BukExo" +
            "ZYa2y7YsywV0hSBE6pnGv97ayQeN+EL5U2WSWFwbEd8EQzIlZQR3wBAcZ3904xKLgz7WACA5YHte29JoiW2wQrnOK+s75LxgGhzG" +
            "zLs6oM20WwZ4hVXpQAqkDxtAaImuTJ+E1+kYu05Q0fss2nyGhwq00AgL1Cws+lskxhE6+Im2SgVQUy+f98CTBNqlOGpwpctm34cl" +
            "hGHQcm8jFySfxzNUlHXIwYWBQZrZMnBT0anKA/dPxYxllqMdMJYrLKiRxlNQdSXB90KYHUoTRaR8AUFTEFw84loUGZxzZw04/wh0" +
            "scbA8HcgnOl3VAZVHTmig46iF8Io3ly8PbbUPXQtM+BHfXsUYyKQIk2nY6gVxB0Faknp7I2bxbOGILnN7t5EVEqmw1p3EKYxUcB3" +
            "A4tk00OL8K/twjhj+Nfc254U5RzkH4Mvog16IEvBlEwvwdgUqoFBF+YReQy6wph9ewjleWV6MArnh9h1UEeyCCBLlyTTfPjC2qe0" +
            "qyl4EjR7ickPU/CRXRqkg5lMwCOBXFwiPJdZU5gI1Zh+y1cK5oIBBDqkdSMDF3tsi4FJRJcCW7XVI06wthixqLJy0MzRTetZwbIV" +
            "IHdENMYyWC9pdH0NGoH1hcG/Op2PVXsb4UrGChsGuBD//re5wWBsIihQ7IgfYeJkD7ERxi12XGlxcKcZ7NF//q1yyAMWyE3AuQB/" +
            "REfIJ+iNMH6GJ2T3rlrQ61yg6HhXhxJoNnhqQI7VhSSiIIS4I2YUUvMixwUQAFm0OZOS1bJUQXwYWJ6O5XoqIg5AwnkJbFWGIazY" +
            "P/39f/kbKIbUZkVAE/VT0NdAOPDW8wUCFEornDpTPxXLBDULy6bqi846W+qFoDVhMtNh4KWWndyFDgAq0J3mJCao6g7IyIb2M+IO" +
            "V9OMrTRSFoVrTTmmxLjNgJkF0PrQFIrcQtUJ7zJpVMo9AD9N01Sp7bFvbS9VmJk/Cr/x0vEAmwVw+o4TmFp+NhaDLQbtUO8UtGyy" +
            "UsuyqsJmzcpAdgY2kKJXlakfJZoykoNxciIQxTRKTJXZAXl2LOZQNb1YXcjY2KdGhoMToNDK+AUYNBo1sUTcRMCJ2Do0YRU0d2io" +
            "gEs8ESfhUSeOZ8zJNBlrh8AFonEtNhLgARp9SUwyg4GAWe5dzj23nZiea8AqJNRKAkC6al9egU+JocR9GxrSFwbhEiKjfkGscxIo" +
            "NvolkWJHisNsDFEwZCOotdCFk7pg8/gB5kGhAIzSiXwOK8rT0g2YA4bjwbNScXCqoWIMWV0z0R/oRIGAy8QFuiPx9seIXmD6GbZw" +
            "xqyKM6bS/SWQHXABcdWnT6WkIHLBMYJUgSvYVGYWEhHKQdOckvyFk9MY3gn8Nyt7gTPZSVR5NPCW0h3dIXRi7qBvGmbF0jQ45kaQ" +
            "ROlaGMgHcsfZ5buQ+nDE/U7dmsGYtlR4yIaAw3pTcLex5fFSShqlNvMOcBwJLDAsURnKQKe6GEapSJIXMQBtCXVDmxXolvfS62lZ" +
            "gosn9L04Qsi7laU6YBNsmqbr6I1aw5hPRd/SCoGGnqo8/LdEARdFTm1TJ0f541/+d+UCe4T8UZDSU902uKuYBDwlu8rqSXQT4Ucl" +
            "D//AaKK4kbLPo5jgDgv2XyoWE4cMPTdqV/WBALJC7KmC5NIJrJOYgT3RXAzJAm+5nK1cwVFUmlA8+9jSR8lnIg81keWTC6azWjJH" +
            "xBnbVaIZkGQTyXSoaGaR30+eaXmiuozJZb2VBfu5ejQCK/PuDOAzwYVBr8B1BjCblJWznoGgBBT+l18GT59qgZBGi0nj/T000m3c" +
            "30OFLrhrRTSPGl9l7IOwuQDtmc1Q2MnSIpbnBzERq/3tv2Hh3i7MwEZmpLJeDPYCPAfBcJc99rLbgGchBaRlAgENul6pOQr3gHS1" +
            "boGVYZ5bMMlCLyx6cMDJqGwpSLaH/xFtvzv6Nost5GBvndvgwOJhjxojd08tOjfOB6TG3/1OOZaE7vuH38PSc9WqFlRCkKsEhPyF" +
            "HK4XTOU8wnaIEimU0zzq8USxMgFPU7F9cL0AfwEeIDPHB6hu8jgIq77OkQmp66SADAoWq3AeqKMpqpz9ouj30DVAP4q+Buj61rjr" +
            "W1MkKgk8wgeVOzq4Xj34P+jaQzw7orWkvSKT7hZxOtQUmjqOfJAhq9YwGo1a2THYaK588YEN2PJ/9IShgc+YMNTuwf9/mglLw06m" +
            "QeYHRkGVsNASjwoL62CFi7V6hrJMyMw/9CNQ2cj4bfXQ/pHEspJnwb0KH006dgCu2n/9W+VUScDzB2008mDk+EJFwp48/GMCgpF5" +
            "cont36DfRydR6fSdA2CGmYIwPvwDskEUYi8KngGxS+4fd/qyOCFiDTbHIrpZ4/656PIYa9wYh/owMZE9vdQCltKEB7GFL0PhTqx2" +
            "NKcBYjQJ58sGfk1F0KHrKi46SyGzjg22kwqANcyeYtJZM8lkLHt+iX1D0AwuvmA+YB4g5RkYtKtwCrnv8WXuexTWITemaECRbOvX" +
            "hdOU2tS1pekiidKr/Byw2yF46Pkmm+EU9ighk84Aut5jlnivIXaMOBw6CpHsBLVFFJNEuXn4vQ9oLffsZF80noYXeDjIzlAQI2Y6" +
            "y0MGFiucznBKWszQcuZjCRASWcK/ur+fL7BMtIQfOOaICmgEEUWWkWMRQBjpGE/AIi0GFAmprE9A2gsYDO4xxgZucNtt1xAttuHB" +
            "YK204cegqLTNdZTBB9TG4RhFj6DtsH1WY9l9aGfbn0h+I/No2hh0yzcZDLFh1wYfn8Q3JEYjnKR2AGJvFByjcs08s7o2gGrAnYVN" +
            "NIF+UbEdF9A5b4mHORkwZvCAT3ELD088GiSYmezYDfXSOeV76mnRAaa4ZBR5XCG7kQkq+gRVIiZOoiR5+MMNmFOmUjM4Lu+n4Xaw" +
            "tDsU2pm+xSw0NpeXxUMTsm1I1CvjUgU+nwYR5JyxB0zL0TykH+YvmMeOU4JAGupAPF5dUagOjK8Ky0U36fHQBtjc2WXjCu0pHxtY" +
            "KsywLEzvqVHITRiOd3bZvJJsEkj99xGAe74NBRPsqkuAnuP/uaSdqBuVaacKtF1ActA4I6UYWIFmPdrY2+hG09s8m9Osx4/n5hkS" +
            "4XrcmYFMRrKvPXKLYZjcnD16yAFalY83DK3usBiZxG6H2Q4GvJVcItabeMsJl08p84P4fi/koPa8v/+J/KJOxp1r3ZqSCyLB/iIu" +
            "kb5oYDnCu+jvD87PT5WL04v+ceZTjFtdid/3tuFdQIwyQFnlTC3DT04hEVx36XOGL122V8B2QjXwBUvexoo+xXmdLqpPJYq9ay+0" +
            "/SUHbtlJWd5//bn8DqTDKtfjX7R/ARPbX+Vi/Kx+BHW+KxwJGCDN2tibkJrnB2pra3dxoINzQILU0Q566jm1QAqNc1PQDjy2DwZ7" +
            "jS9RbJx9aJ61fUB86spA09BS5sigFzNgPrTc8NIuVBYoqFIaa7YUV+sR0AtvwBg8/COsMVcT4kQE3eFnO93gRkrD0hI9j12Uqcw/" +
            "qWIEzFvo0efMVEoVsuAOX/q9IQvXrIxjYrRySR/MRNCC7e1yxilGXwosUxlZFcHQ/TxKs6KWFHkVI6QHnSrGR9Oz0RXolmm3qi4+" +
            "IQZcoRNzGNEukJ1++VXrnpBwPA3k1RVBHNpWie24X7rkPLHYrY3ngFZjEWFuV599ZuZF+DuPu1JMD3+EN8X0W16BAlRAKpKh38iR" +
            "+tKlYc1VztQ+DRaAMUuEP5W5UfwEqX3nR7ZrfYYf8nmeAZ2EmL1+OsQD+CbwhXcdanxweDjwZ3R1FkUXE0DdAXi6K/3dbJBgna4J" +
            "JclC+CBiXsVwEOpwwBbM2Shk0FF9irOzkc9SJSpgBn5KSaGrmbHw/f2XPFqlqcKolLZsiJJFxHtqtlMl098leNhi3RJkRJBpSQPB" +
            "D39AIn8a+UQkucLnG3kYcCZuhtzTfAcjslLp2wKkBz8jb/FT8nrx2Bq2VDi4lpYq4ebN2jo9qT+oEESlBuww2rSBXWwglV4z1wEr" +
            "Fg7EcqdrLqJ/OU2otrQBiLngvGtaYszAS0q2Ko2Hwb2WNcWl7Z1HPJkivKDetwAi+U0y7GIj7pazzxbUN/iDfjXbKHv78L+ZW46U" +
            "M9R+yJzudOri6wX+rHK2OdUr/e1sRT7a5S4jG/r1VwlMpXYqIOtByaGosu1AjYrtIXXLznEWfqO2Dl/JXe5X4f2qfqu3pYA0D3+I" +
            "QPUl6H8Dg9FjSJt0XAHPN90LI8xdSUodVW7HMVj7CHx9y7USC6rLnu4KVCqI3bMfw6M/ARIVK7wJ8hTvvN3HYKdavZn4/wmwxP11" +
            "eVNdCcHSE0Xw3EYok2m5d/2Ds/7pdweD83eD8/65dan2Tw7OBuenJ8p5/7h/dnQK+mH/6Lh/Lr1nRZo7ShOaONsfHPeVZmu72cpK" +
            "N3f+7/+BTPinnH/Sv+gfH/Wl9kRKRZ0mq/NV//XZ0eBYHsPx+6/eQ8rx6a/6yteD4zf9cuLJ6dc07av3fWWgHL//C3g5OrkYnJ0M" +
            "LlAlDo6U/ZN3v8LH04tT5aL/7RE8Hwy+NpWDoxPF3D8+GkBx9Ur6tguwwr4fJRgiEfFRcbhAOq8NOhetljiuLZ/WTscgUolUfSP7" +
            "p5faOJ8GtA1hqeSGO+IzM7yi5qNMVntTk8Wh85rin3LEpiF/QkJnhNEXjxSplc+edZJXcdl+0oBfP5FHsEs8zo5/EPxi17EjPAaS" +
            "vRjUo2g3FoXzHWQ2OYtuYXXygqCE8rKVn2HwL5ArD+Yq2E6NmVd8LB4QnplZN/xDiVKo54BmU41c2zwERXuDjh6JP2VrQ6eHS9Mr" +
            "JumfF5oqRnDoRR2cEnHQffhPpaiMHJXPAt+c7QMRW8YMQj82u78P2DdnhmNJoorbX+COWv04tu9ML6G/moN0npDETvSnT/MXYcDy" +
            "lPYSZxn+1BFhaceElzgClYzrAt1HvR5P6/VAVkhqkdlqScnIjHIVJJbM4QasjsO+0AvsGWiDIGFMX4fmYRDeDxY81En6c0XDUZIe" +
            "/upUOegr7477J0egdeWgeL6lo8C/CaKYcmh8dagVIepjp+/zlRZh8oC9ZPwbFM7UF+OfNBApR8PYKSRQ9m8GR2en6yAXEL4MLzMY" +
            "VGjs7eDMfNf/6lTRCvhRf6TxlfFVxEnZYSl++mq4shl24G+5ocdof/x+/+xUeX32/uL0UyLblNkfC2xDoR78/08R2F5aWFiF/f7B" +
            "6Rlgij/+5d9luzflgDeKV1XEG9PXrzU2qBwMFGFTKhoHA1DVNklXL/Txw7/71++PDk6Vw6MTHPCw0GBI0lJboABWLvayKaLXQWGA" +
            "vpuPelhWyNklsCKq7oLWq3W3lL7rUS8mLsfNlztKaoIA7A03mGbU6jJLWuEJrtk0oGdHUO4B9rINg3w3dM2HIKeJ4jz83nemUBV8" +
            "sOspQOZIAac5RqF0bfq5ETBa6Plju628hSXHPVvPVix+XNclyg0BWiTKH//md8ox8rjyOsY7yfjL8cMffph6IJeWnEtLHwhjshp/" +
            "S7vNNhI5xyuXeBx1zddrnLCqzjz2uABh2nH5m9fLDGdclb6X4BAGo7orakGBrBIejS0i1RymCGywkGZFbzQSxppY2TQNMIhL9pA3" +
            "gEbQXx9mpsoGP93kYeZgzSGszFxk5e/vtdWo4WMMMDvzs/LrFC78qq5vUGbN8a51nWTqa10vUqFV3VB1t7YnUD3r+qDZq1r366le" +
            "fSCMorMczW20jHz3YjMeyXYiNme+j2AAhr/q6Qa7GmuD7/IZdwO6nIP72A4MOoe2b5TRJSRlwJRkj3SJ2ykdL9NjLAXfDTxX4uN3" +
            "cMceVVhtGDnz4URcrc0ADktEXfjOvqaJ2UYJPUfeFtOnJ7VyuLe0fSFlFbcvPm9npbQjUX38SkKhuF/wp9t8kB3E7CDO/DOgOGun" +
            "CMaH3nWt+8f/+D/FJ7Wt7jtutJQwt2cXg/03yv7g+Fjp758z1J19ZksvDKt1vyKxrUwDW7GHYPjsmH1mqwQAuw0lsHF8YMoEGDYy" +
            "JGuwz2cU13v4fexFhiJYzlAoaypDtHrQRG5BCc/xuYGkX/HuTVjMr7DzjQiktPtNA3S0ZBZZKZTOxYWGcifrvxtlFBUx8/zvDtS6" +
            "r5GhY2Uwc4DymjnzsbUM2qyx17PAH4CbfpMttbgkqdcrfGL/FL+wZzcbSal7NBXvOJISuzTxuphYo4ns3iPJsOKdWNoN6DGV9Yt3" +
            "eOHkoO+c9/ahEGBumtNTlSRpn+Pj0QFCcZq6Ra9Voocj9ujXHVDmgrpU6lbKI/hiokh5LAPUwIbl2yRoQBITtVLv23Ip4HkNx51U" +
            "jRUQYfVQf5OP9Te1/Dw6bagAIaEFuTeqwC/Qe81jZJd3RhBcWYGZTHwP5LuOV13ZGKi7VH/VP0GWxzOJg68HZ/z5bf/s4a9ppPH1" +
            "2dExTaCBx1+9P3nDfo/pL4jI+QU+nA8uBm9f07qn7y/es6eT069F4sHgL9jjFXfXNTqAS3G8LdDrzSvAIgFGn9WtOymyZd8lRyG7" +
            "TmfdjCgcZM1l20qZYcLiYOHyC3ikRUQZoIleEtGt0y/xgbWgqp28Q8O9siBL6jO7CWhL3abu93Zh6MmYkPTbwF8VreSBTvgFOdW0" +
            "mXGnZzeH8a0Bk1oQsh8FeF+iuJLsjmUiZrMnFt9kH8VRIIKG2QbEJWvHmF3pP3Fc6PJqCS98TGBoNsmLFux4r7dpxIgDlKyZkvnv" +
            "9VYFjxh6yeqVEUKvp9HI0mwCJUOryIAGiDNeYNLBX/bVHQr4JdVM6kX/NUbuwYuln5CiYHA9pVKZpJ92Z0pjo2fW8CHYuP7bwcnF" +
            "Kdimk/P+8ac2zZo7G3x1dH5xdoojPe6fPPw1a/uRRvHGucppHxwVpvqGXlGjis6EWV1TRNjbNUV42AGGvKKz5VlKkZ01DX/dPz49" +
            "e7TF5dbfnJ6tm/X78/cPv2XbNUtFkJDgIWh4e59rNTvunhV23K0toSZAy1hBfm+YbswskOdLSL6SzwvPVp8XnhW+owTWlzLE/oTY" +
            "StXxDzAoIzBMxjX8Q50hy7Ja+hzSZBp2rjHkuuD5L3TIFtGUrNYrWguS98+O9vu0Dqcw/xSdeJZbbzbpJTJe12o8fUq8PRB5pmGw" +
            "NpldEu+quBEAzfAJ8NxMEfBmE4DB9l3uigjDIh9+MEqJz4wXOtgdw+WG4U7DDadGJY8XzUS+pqzfnnpOf+nZmbbKjtAwRkDPQmUd" +
            "l0vTD5DofRP4y8uj0/Ex5WE1Ny2/xMOjVfNQ29oIz5pky3t/L975uvYEMyM0AQ4D9BSNlGssFLLB9GgX1xsOri1KrxqQ+phAznrl" +
            "L9DXN1aoRI9SPlblqipR7yxK/LI0Mu5fZoRQeUwiyy2s+VIuczqX85dV0lJodrV+Auv2SJMbPFcYhI81b5vbLKaJjo9olHj1xNC9" +
            "33hiKy1aHkR/zCStzqfhh8+m8SetiXAvvoniDxSFomtB7++rZa5NwVNg1zFd4KFx8DIoSWQng2XsbWftFZxC5mh+6yd5QAvbBphk" +
            "miZq5HOSavgsH9Ve3uzX6V0ewu/NioGboF+J2OvrKPKJHeqQQkEzOzTLuhMmhL/RJRXBTXGxEXpUiaXuUTcr6bJfJA56h/yjTCBE" +
            "H+/3Rj8bs74mcQp2yLdq++xSsO3u3mHE8vCX0XXf9r1h7NWoE+f9CCnNJpbcpl2Uerrg24uimdeRDw57U67cwsr0LlQvijF9H+8m" +
            "t2pPDl4N+ocvaNF3Nv7hpNCqnePN5yt7Y2xZ0d2KHgatw8bBqxU9vKbXUCTigRbiN8dbtdf0b6rQmsdeSM5Z5AEDo144jaYJzfmG" +
            "XqbO+9/O2qsc+lu+/SZfVc3ojk9W7ewXyhPjyZOG2WisJjbbiaqa/YbNVhHp8PCwtb//MctAv3Ba3afrbgfB9h38Z2ULzOxIUxGD" +
            "2W/gf2ql2a1pQ9B1s4Y+h/o5MPrs7qoptM1lWTVAsYFo9+BHuQGWohzZNBu1XpcqwmEUfcAAVphYNVCP7cQZk8BO6oEHXmkSjdK6" +
            "EwXtaDQChdROJhg2p7quxiq1k4+t1+UhnGSLqyTUaSIoUNwIFOODWdA/Z6yKIwZ1jG7WMbpZ7++fg0fATyZvqRi1U8XNvUs3y9o3" +
            "5LWdkOe7h55PhFoe3qWg/VAjX5BZOgidCERP001CnzSYJvMFhuBjAOYWLooHmNjbo5XFBZXelrXTevH8pQ5Ft/h1SzQCsT+2431s" +
            "DHQ5q5FMhzaNLXiGt8Uq4V+LkMdbGi6NDRl4Y73PL8bdvgldM0jqFIWrxjCNbA16zo+KZ3FhGqjGT9YOotsQD/InZhanWay5UpcT" +
            "yI+GlD6v4UG7BIJcGXPEtu2Vo+mIP3ZN/9Y1Xo9vr7xOF2/Cs81xTEbW+7NjnsvCN/CuYe9YwOVDt5AO8E4/TtWWrsjFJmJyE32Q" +
            "mmCt60s3ZkufrM6X7z7Y/LMA6ubpLn4xS6/1Yp/MFr9ztVz+8UDxu9nHL5YQX92su1tCfGiDA7GfPh3mN7XhSNZVe2QjcOX1FCuu" +
            "hlis6wu/WFp7w272XdOaSyzYV32PNwOFNvjumR1XX//pMz/Snn/9zBKWPoDm9+ZuuG2JS+UEuhNUc01e1IIilZzz+H74yntl2Vb8" +
            "BvThu/3SDcYz6R6QDRuJg4o2JALP2GxRY+UnGfLruMW5gcVarsDN3Eevb56v23qnR1OgYw9+47Tvfm/jdbf4Jb+mDgkMnwD9VYOf" +
            "/dSLX7yvu69F7GyvHV5Wak1LzKVY20zmdSzvsIMlBddg/v/8ft99dgcr7i3a+YU+YBmrbvZ95GbW/LpobMNSx2k6Sdrb27e3t+Y1" +
            "fkLhOfhXYLbFX6z5PtlWtw6/Ni77xnvj4IrfmvIOTLQHQ7CBZy+9ACmoYektNatYR7J9j1fOrcqfpuO1BfABv4ogtNSVboxsqw8r" +
            "AU4CbnGT/mSi7R9+BYoVWrLe4+L34Ukb2cARQ+uA/l0F0QZNPbTmQLX36CAeLDrvQaiwwjnMmrDrJfldpVN2seSX0+zOT35ntEAA" +
            "9OroaYfe9qlNpRvZizdBWpZF/xJPj38/x/84kLp8U+jTp2uaEVdJ6gJu3NpxSL/iTG0lxL1rvKIUd6LT2Ka/sIL8qCPoTXF/aUd0" +
            "yQbTK2jHduHm0DV/NeCnve76y1wMDm0EXi67cnjGPpVZEgVwor2RB3pTwetd0V0CYAkvwxj38m/opjZhe+XL+8+LL5hEdxY6/IN/" +
            "nIr9xSkQR/pXqbbHIETdL/4ZxTxp9KiEAAA=";

    static String html() {
        try {
            byte[] compressed = Base64.decode(DATA, Base64.DEFAULT);
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "<html><body><h3>Erro ao abrir Caixa da Loja</h3><p>" + e.getMessage() + "</p></body></html>";
        }
    }
}
