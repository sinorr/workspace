#include<string.h>
#include<iostream>
#define MAXN 10005
using namespace std;

int h[MAXN];
int main()
{
    int r, l, hig, maxer = 0;
    memset (h, 0, sizeof(h) );
    while( cin >> l >> hig >> r )
    {
        maxer = maxer > r ? maxer : r;
        for ( int i = l; i <= r; i++)
        {
            h[i] = h[i] > hig ? h[i] : hig;
        }
    }

    int i = 0;
    while (!h[i])
        i++;
    cout << i << ' ' << h[i];
    maxer ++ ;
    while( ++i <= maxer )
    {
        if ( h[i] != h[i-1])
        {
            if (h[i] > h[i-1])
                cout << ' ' << i << ' ' << h[i];
            else
                cout << ' ' << i - 1 << ' ' << h[i];
        }
    }
    cout << endl;
}
