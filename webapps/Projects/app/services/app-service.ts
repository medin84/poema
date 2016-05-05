import {Injectable, Inject} from '@angular/core';
import {Http, Headers} from '@angular/http';

const HEADER = { headers: new Headers({ 'Accept': 'application/json' }) };

@Injectable()
export class AppService {

    constructor(
        private http: Http
    ) { }

    getTranslations() {
        let url = 'p?id=common-captions';
        return this.http.get(url, HEADER)
            .map(response => response.json().captions);
    }

    getUsers() {
        let url = 'p?id=users';
        return this.http.get(url, HEADER)
            .map(response => response.json().objects[0].list);
    }

    getNav() {
        let url = 'p?id=outline';
        return this.http.get(url, HEADER);
    }

    logout() {
        return this.http.delete('');
    }
}
