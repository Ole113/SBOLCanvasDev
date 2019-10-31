import { AfterViewInit, Component, ElementRef, OnInit, QueryList, ViewChild } from '@angular/core';
import { GraphService } from '../graph.service';
import { FilesService } from '../files.service';
import { MatDialog } from '@angular/material';
import { SaveGraphComponent } from '../save-graph/save-graph.component';
import { LoadGraphComponent } from '../load-graph/load-graph.component';
import { UploadGraphComponent } from '../upload-graph/upload-graph.component';
import { LoginComponent } from '../login/login.component';
import { Observable } from 'rxjs';

export interface SaveDialogData {
  filename: string;
}
export interface LoadDialogData {
  file: File;
}
export interface LoginDialogData {
  email: string;
  password: string;
  server: string;
}
export interface UploadDialogData {
  collection: string;
  filename: string;
}

@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.component.html',
  styleUrls: ['./toolbar.component.css']
})
export class ToolbarComponent implements OnInit, AfterViewInit {

  @ViewChild('backbone') backbone: ElementRef;

  filename: string;
  user: string;
  server: string;

  constructor(public graphService: GraphService, private filesService: FilesService, public dialog: MatDialog) { }

  ngOnInit() {
  }

  ngAfterViewInit() {
  }

  save(filename: string) {
    this.filesService.saveLocal(filename, this.graphService.getGraphXML());
  }

  load(file: File) {
    this.filesService.loadLocal(file, this.graphService);
  }

  openLoginDialog(): Observable<string> {
    const loginDialogRef = this.dialog.open(LoginComponent, {
      data: { user: null, server: null }
    });
    this.filesService.getRegistries().subscribe(result => {
      loginDialogRef.componentInstance.servers = result;
    });

    // this is total garbage, but the way observables work I have no choice
    // the outer observable doesn't notify any of it's subscribers, until the user token is set
    return new Observable((observer) => {
      loginDialogRef.afterClosed().subscribe(result => {
        if (result == null) {
          observer.complete();
          return;
        }
        this.server = result.server;
        this.filesService.login(result.email, result.password, result.server).subscribe(result => {
          this.user = result;
          observer.next(this.user);
          observer.complete();
        });
      });
    });
  }

  openUploadDialog(): void {
    if (!this.user) {
      this.openLoginDialog().subscribe(result => {
        if (result != null) {
          this.openUploadDialog();
        }
      });
    } else {
      const uploadDialogRef = this.dialog.open(UploadGraphComponent, {
        data: { collection: null, filename: null }
      });
      this.filesService.listCollections(this.user, this.server).subscribe(result => {
        uploadDialogRef.componentInstance.collections = result;
      });

      uploadDialogRef.afterClosed().subscribe(result => {
        if (result != null) {
          this.filesService.uploadSBOL(this.graphService.getGraphXML(), this.server, result.collection, this.user, result.filename).subscribe();
        }
      });
    }
  }

  openSaveDialog(): void {
    const dialogRef = this.dialog.open(SaveGraphComponent, {
      data: { filename: this.filename }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result != null) {
        this.save(result);
      }
    });
  }

  openLoadDialog(): void {
    const dialogRef = this.dialog.open(LoadGraphComponent, {
      data: { file: null }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result != null) {
        this.load(result);
      }
    });
  }

  testReplace(): void {
    this.graphService.setSelectedToXML("<root><mxCell id=\"4\" value=\"\" style=\"sequenceFeatureGlyphPro (Promoter)\" vertex=\"1\" parent=\"2\" collapsed=\"1\"><mxGeometry width=\"50\" height=\"100\" as=\"geometry\"/><GlyphInfo partType=\"DNA region\" partRole=\"Pro (Promoter)\" displayID=\"id1\" as=\"data\"/></mxCell><mxCell id=\"5\" value=\"\" style=\"circuitContainer\" vertex=\"1\" connectable=\"0\" parent=\"4\"><mxGeometry width=\"50\" height=\"100\" as=\"geometry\"/></mxCell><mxCell id=\"6\" value=\"\" style=\"backbone\" vertex=\"1\" connectable=\"0\" parent=\"5\"><mxGeometry y=\"50\" width=\"50\" height=\"1\" as=\"geometry\"/></mxCell></root>");
  }
}
